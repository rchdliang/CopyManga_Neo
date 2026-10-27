#define VK_NO_PROTOTYPES
#include <vulkan/vulkan_core.h>
#include "engine.h"
#include <algorithm>
#include <array>
#include <cstring>
#include <limits>
#include <map>
#include <stdexcept>
#include <vector>
#ifdef _WIN32
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <dlfcn.h>
#endif

namespace restore {
namespace {
void require(bool value, const char* message) { if (!value) throw std::runtime_error(message); }
void check(VkResult result, const char* message) {
    if (result != VK_SUCCESS) throw std::runtime_error(std::string(message) + " (VkResult " + std::to_string(result) + ")");
}
struct Reader {
    const uint8_t* data; size_t length, offset=0;
    uint32_t u32() {
        require(offset <= length && length-offset >= 4, "Truncated model");
        uint32_t v=uint32_t(data[offset]) | uint32_t(data[offset+1])<<8 | uint32_t(data[offset+2])<<16 | uint32_t(data[offset+3])<<24;
        offset+=4; return v;
    }
    std::vector<uint32_t> code() {
        auto bytes=u32(); require(bytes>=20 && bytes%4==0 && bytes<=length-offset, "Invalid SPIR-V size");
        std::vector<uint32_t> result(bytes/4);
        for (auto& word:result) word=u32();
        require(result[0]==0x07230203, "Invalid SPIR-V magic");
        return result;
    }
};
#define INSTANCE_FUNCTIONS(F) \
 F(DestroyInstance) F(EnumeratePhysicalDevices) F(GetPhysicalDeviceQueueFamilyProperties) \
 F(GetPhysicalDeviceProperties) F(GetPhysicalDeviceMemoryProperties) F(GetPhysicalDeviceFeatures) \
 F(GetPhysicalDeviceFormatProperties) F(CreateDevice) F(GetDeviceProcAddr)
#define DEVICE_FUNCTIONS(F) \
 F(DestroyDevice) F(GetDeviceQueue) F(CreateBuffer) F(DestroyBuffer) F(GetBufferMemoryRequirements) \
 F(AllocateMemory) F(FreeMemory) F(BindBufferMemory) F(MapMemory) F(UnmapMemory) \
 F(FlushMappedMemoryRanges) F(InvalidateMappedMemoryRanges) F(CreateImage) F(DestroyImage) \
 F(GetImageMemoryRequirements) F(BindImageMemory) F(CreateImageView) F(DestroyImageView) \
 F(CreateSampler) F(DestroySampler) F(CreateShaderModule) F(DestroyShaderModule) \
 F(CreateDescriptorSetLayout) F(DestroyDescriptorSetLayout) F(CreatePipelineLayout) F(DestroyPipelineLayout) \
 F(CreateComputePipelines) F(DestroyPipeline) F(CreateDescriptorPool) F(DestroyDescriptorPool) \
 F(AllocateDescriptorSets) F(UpdateDescriptorSets) F(CreateCommandPool) F(DestroyCommandPool) \
 F(AllocateCommandBuffers) F(ResetCommandBuffer) F(BeginCommandBuffer) F(EndCommandBuffer) \
 F(CmdPipelineBarrier) F(CmdCopyBufferToImage) F(CmdBindPipeline) F(CmdBindDescriptorSets) \
 F(CmdPushConstants) F(CmdDispatch) F(CmdCopyImageToBuffer) F(CreateFence) F(DestroyFence) \
 F(WaitForFences) F(ResetFences) F(QueueSubmit) F(DeviceWaitIdle) F(CreatePipelineCache) F(DestroyPipelineCache)
}

struct Engine::Impl {
#ifdef _WIN32
    HMODULE library=nullptr;
#else
    void* library=nullptr;
#endif
    PFN_vkGetInstanceProcAddr getInstance=nullptr;
    PFN_vkCreateInstance CreateInstance=nullptr;
#define DECLARE(name) PFN_vk##name name=nullptr;
    INSTANCE_FUNCTIONS(DECLARE)
    DEVICE_FUNCTIONS(DECLARE)
#undef DECLARE
    VkInstance instance=VK_NULL_HANDLE;
    VkPhysicalDevice physical=VK_NULL_HANDLE;
    VkDevice device=VK_NULL_HANDLE;
    VkQueue queue=VK_NULL_HANDLE;
    uint32_t family=0;
    VkPhysicalDeviceMemoryProperties memory{};
    VkPhysicalDeviceProperties properties{};
    VkCommandPool commandPool=VK_NULL_HANDLE;
    VkCommandBuffer command=VK_NULL_HANDLE;
    VkFence fence=VK_NULL_HANDLE;
    VkSampler sampler=VK_NULL_HANDLE;
    VkPipelineCache pipelineCache=VK_NULL_HANDLE;
    VkBuffer staging=VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory=VK_NULL_HANDLE;
    uint8_t* mapped=nullptr;
    bool coherent=false;
    static constexpr uint32_t edge=tile_size+64;
    static constexpr VkDeviceSize transferBytes=VkDeviceSize(edge)*edge*4;
    uint64_t clock=0;

    struct Image {
        VkImage handle=VK_NULL_HANDLE; VkDeviceMemory memory=VK_NULL_HANDLE; VkImageView view=VK_NULL_HANDLE;
    };
    struct Pass {
        std::vector<uint32_t> inputs;
        uint32_t output=0;
        VkDescriptorSetLayout setLayout=VK_NULL_HANDLE;
        VkPipelineLayout layout=VK_NULL_HANDLE;
        VkPipeline pipeline=VK_NULL_HANDLE;
        VkDescriptorSet set=VK_NULL_HANDLE;
    };
    struct Model {
        Impl& owner; int halo=0; bool initialized=false; uint64_t used=0;
        VkDescriptorPool pool=VK_NULL_HANDLE;
        std::vector<Image> images;
        std::vector<Pass> passes;
        explicit Model(Impl& o):owner(o){}
        ~Model() {
            auto& o=owner;
            for(auto& p:passes) {
                if(p.pipeline)o.DestroyPipeline(o.device,p.pipeline,nullptr);
                if(p.layout)o.DestroyPipelineLayout(o.device,p.layout,nullptr);
                if(p.setLayout)o.DestroyDescriptorSetLayout(o.device,p.setLayout,nullptr);
            }
            if(pool)o.DestroyDescriptorPool(o.device,pool,nullptr);
            for(auto& image:images) {
                if(image.view)o.DestroyImageView(o.device,image.view,nullptr);
                if(image.handle)o.DestroyImage(o.device,image.handle,nullptr);
                if(image.memory)o.FreeMemory(o.device,image.memory,nullptr);
            }
        }
    };
    std::map<int,std::unique_ptr<Model>> models;

    explicit Impl(bool allowSoftware) {
        try { init(allowSoftware); } catch (...) { shutdown(); throw; }
    }
    ~Impl(){shutdown();}
    template<class T> void instanceProc(T& target,const char* name) {
        target=reinterpret_cast<T>(getInstance(instance,name)); require(target!=nullptr,name);
    }
    template<class T> void deviceProc(T& target,const char* name) {
        target=reinterpret_cast<T>(GetDeviceProcAddr(device,name)); require(target!=nullptr,name);
    }
    void init(bool allowSoftware) {
#ifdef _WIN32
        library=LoadLibraryW(L"vulkan-1.dll");
        require(library!=nullptr,"Vulkan loader is unavailable");
        getInstance=reinterpret_cast<PFN_vkGetInstanceProcAddr>(GetProcAddress(library,"vkGetInstanceProcAddr"));
#else
#ifdef __ANDROID__
        library=dlopen("libvulkan.so",RTLD_NOW|RTLD_LOCAL);
#else
        library=dlopen("libvulkan.so.1",RTLD_NOW|RTLD_LOCAL);
#endif
        require(library!=nullptr,"Vulkan loader is unavailable");
        getInstance=reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library,"vkGetInstanceProcAddr"));
#endif
        require(getInstance!=nullptr,"vkGetInstanceProcAddr unavailable");
        instanceProc(CreateInstance,"vkCreateInstance");
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName="CopyManga Restore"; app.apiVersion=VK_API_VERSION_1_0;
        VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ci.pApplicationInfo=&app;
        check(CreateInstance(&ci,nullptr,&instance),"Create Vulkan instance");
#define LOAD_I(name) instanceProc(name,"vk" #name);
        INSTANCE_FUNCTIONS(LOAD_I)
#undef LOAD_I
        uint32_t count=0; check(EnumeratePhysicalDevices(instance,&count,nullptr),"Enumerate GPUs");
        std::vector<VkPhysicalDevice> devices(count); check(EnumeratePhysicalDevices(instance,&count,devices.data()),"Read GPUs");
        int best=-1;
        for(auto gpu:devices) {
            VkPhysicalDeviceProperties props{}; GetPhysicalDeviceProperties(gpu,&props);
            if(!allowSoftware && props.deviceType==VK_PHYSICAL_DEVICE_TYPE_CPU)continue;
            VkPhysicalDeviceFeatures features{}; GetPhysicalDeviceFeatures(gpu,&features);
            if(!features.shaderStorageImageExtendedFormats || props.limits.maxPerStageDescriptorSamplers<16)continue;
            bool formats=true;
            for(auto format:{VK_FORMAT_R8G8B8A8_UNORM,VK_FORMAT_R16G16B16A16_SFLOAT}) {
                VkFormatProperties fp{}; GetPhysicalDeviceFormatProperties(gpu,format,&fp);
                auto required=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT|VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT;
                if((fp.optimalTilingFeatures&required)!=required)formats=false;
            }
            if(!formats)continue;
            uint32_t n=0; GetPhysicalDeviceQueueFamilyProperties(gpu,&n,nullptr);
            std::vector<VkQueueFamilyProperties> families(n); GetPhysicalDeviceQueueFamilyProperties(gpu,&n,families.data());
            for(uint32_t i=0;i<n;i++)if(families[i].queueCount && (families[i].queueFlags&VK_QUEUE_COMPUTE_BIT)) {
                int score=props.deviceType==VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU?3:props.deviceType==VK_PHYSICAL_DEVICE_TYPE_CPU?0:2;
                if(score>best){best=score;physical=gpu;family=i;properties=props;}
                break;
            }
        }
        require(physical!=VK_NULL_HANDLE,"No compatible Vulkan GPU (RGBA16F storage required)");
        GetPhysicalDeviceMemoryProperties(physical,&memory);
        float priority=1;
        VkDeviceQueueCreateInfo q{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};q.queueFamilyIndex=family;q.queueCount=1;q.pQueuePriorities=&priority;
        VkPhysicalDeviceFeatures enabled{};enabled.shaderStorageImageExtendedFormats=VK_TRUE;
        VkDeviceCreateInfo dc{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};dc.queueCreateInfoCount=1;dc.pQueueCreateInfos=&q;dc.pEnabledFeatures=&enabled;
        check(CreateDevice(physical,&dc,nullptr,&device),"Create Vulkan device");
#define LOAD_D(name) deviceProc(name,"vk" #name);
        DEVICE_FUNCTIONS(LOAD_D)
#undef LOAD_D
        GetDeviceQueue(device,family,0,&queue);
        VkCommandPoolCreateInfo cp{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};cp.queueFamilyIndex=family;cp.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        check(CreateCommandPool(device,&cp,nullptr,&commandPool),"Create command pool");
        VkCommandBufferAllocateInfo ca{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ca.commandPool=commandPool;ca.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ca.commandBufferCount=1;
        check(AllocateCommandBuffers(device,&ca,&command),"Allocate commands");
        VkFenceCreateInfo fc{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};check(CreateFence(device,&fc,nullptr,&fence),"Create fence");
        VkSamplerCreateInfo sc{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};sc.magFilter=VK_FILTER_NEAREST;sc.minFilter=VK_FILTER_NEAREST;sc.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
        sc.addressModeU=sc.addressModeV=sc.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        check(CreateSampler(device,&sc,nullptr,&sampler),"Create sampler");
        VkPipelineCacheCreateInfo pc{VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO};check(CreatePipelineCache(device,&pc,nullptr,&pipelineCache),"Create pipeline cache");
        VkBufferCreateInfo bc{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};bc.size=transferBytes*2;bc.usage=VK_BUFFER_USAGE_TRANSFER_SRC_BIT|VK_BUFFER_USAGE_TRANSFER_DST_BIT;bc.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        check(CreateBuffer(device,&bc,nullptr,&staging),"Create staging buffer");
        VkMemoryRequirements req{};GetBufferMemoryRequirements(device,staging,&req);
        uint32_t type=findMemory(req.memoryTypeBits,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,false);
        if(type==UINT32_MAX)type=findMemory(req.memoryTypeBits,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,true);
        coherent=(memory.memoryTypes[type].propertyFlags&VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)!=0;
        VkMemoryAllocateInfo ma{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};ma.allocationSize=req.size;ma.memoryTypeIndex=type;
        check(AllocateMemory(device,&ma,nullptr,&stagingMemory),"Allocate staging memory");
        check(BindBufferMemory(device,staging,stagingMemory,0),"Bind staging memory");
        void* ptr=nullptr;check(MapMemory(device,stagingMemory,0,VK_WHOLE_SIZE,0,&ptr),"Map staging memory");mapped=static_cast<uint8_t*>(ptr);
    }
    uint32_t findMemory(uint32_t mask,VkMemoryPropertyFlags flags,bool required) {
        for(uint32_t i=0;i<memory.memoryTypeCount;i++)if((mask&(1u<<i)) && (memory.memoryTypes[i].propertyFlags&flags)==flags)return i;
        if(required)throw std::runtime_error("Required Vulkan memory type unavailable");
        return UINT32_MAX;
    }
    void makeImage(Image& image,bool input,bool output) {
        VkImageCreateInfo ci{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        ci.imageType=VK_IMAGE_TYPE_2D;ci.format=(input||output)?VK_FORMAT_R8G8B8A8_UNORM:VK_FORMAT_R16G16B16A16_SFLOAT;
        ci.extent={edge,edge,1};ci.mipLevels=1;ci.arrayLayers=1;ci.samples=VK_SAMPLE_COUNT_1_BIT;ci.tiling=VK_IMAGE_TILING_OPTIMAL;
        ci.usage=input?(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT):output?(VK_IMAGE_USAGE_STORAGE_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT):(VK_IMAGE_USAGE_STORAGE_BIT|VK_IMAGE_USAGE_SAMPLED_BIT);
        ci.sharingMode=VK_SHARING_MODE_EXCLUSIVE;ci.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        check(CreateImage(device,&ci,nullptr,&image.handle),"Create tile image");
        VkMemoryRequirements req{};GetImageMemoryRequirements(device,image.handle,&req);
        VkMemoryAllocateInfo ma{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};ma.allocationSize=req.size;ma.memoryTypeIndex=findMemory(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,true);
        check(AllocateMemory(device,&ma,nullptr,&image.memory),"Allocate tile memory");
        check(BindImageMemory(device,image.handle,image.memory,0),"Bind tile memory");
        VkImageViewCreateInfo vi{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};vi.image=image.handle;vi.viewType=VK_IMAGE_VIEW_TYPE_2D;vi.format=ci.format;
        vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        check(CreateImageView(device,&vi,nullptr,&image.view),"Create tile view");
    }
    void load(int key,const uint8_t* bytes,size_t length) {
        require(key>=0&&key<10&&length<=16*1024*1024,"Invalid model key or length");
        auto found=models.find(key);if(found!=models.end()){found->second->used=++clock;return;}
        Reader r{bytes,length};
        require(r.u32()==0x31564b41&&r.u32()==1,"Invalid model format");
        auto halo=r.u32(),count=r.u32(),nodes=r.u32();
        require(halo<=32&&count>0&&count<=64&&nodes==count+1,"Invalid model dimensions");
        // Two most recently selected models; release older GPU resources before allocating replacements.
        if(models.size()>=2){
            auto old=std::min_element(models.begin(),models.end(),[](auto& a,auto& b){return a.second->used<b.second->used;});
            models.erase(old);
        }
        auto model=std::make_unique<Model>(*this);model->halo=int(halo);model->used=++clock;
        model->images.resize(nodes);model->passes.resize(count);
        for(uint32_t i=0;i<nodes;i++)makeImage(model->images[i],i==0,i==nodes-1);
        std::vector<std::vector<uint32_t>> codes;
        uint32_t combined=0;
        for(uint32_t i=0;i<count;i++){
            auto& pass=model->passes[i];pass.output=r.u32();auto n=r.u32();
            require(pass.output==i+1&&n>0&&n<=16,"Invalid graph pass");combined+=n;
            for(uint32_t j=0;j<n;j++){auto id=r.u32();require(id<pass.output,"Invalid graph dependency");pass.inputs.push_back(id);}
            codes.push_back(r.code());
        }
        require(r.offset==length,"Trailing model data");
        std::array<VkDescriptorPoolSize,2> sizes{{{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,combined},{VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,count}}};
        VkDescriptorPoolCreateInfo dp{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dp.maxSets=count;dp.poolSizeCount=2;dp.pPoolSizes=sizes.data();
        check(CreateDescriptorPool(device,&dp,nullptr,&model->pool),"Create descriptor pool");
        for(uint32_t i=0;i<count;i++){
            auto& pass=model->passes[i];uint32_t n=uint32_t(pass.inputs.size());
            std::vector<VkDescriptorSetLayoutBinding> bindings(n+1);
            for(uint32_t b=0;b<=n;b++){bindings[b].binding=b;bindings[b].descriptorCount=1;bindings[b].descriptorType=b==n?VK_DESCRIPTOR_TYPE_STORAGE_IMAGE:VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;bindings[b].stageFlags=VK_SHADER_STAGE_COMPUTE_BIT;}
            VkDescriptorSetLayoutCreateInfo sl{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};sl.bindingCount=n+1;sl.pBindings=bindings.data();
            check(CreateDescriptorSetLayout(device,&sl,nullptr,&pass.setLayout),"Create descriptor layout");
            VkPushConstantRange range{VK_SHADER_STAGE_COMPUTE_BIT,0,24};
            VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};pl.setLayoutCount=1;pl.pSetLayouts=&pass.setLayout;pl.pushConstantRangeCount=1;pl.pPushConstantRanges=&range;
            check(CreatePipelineLayout(device,&pl,nullptr,&pass.layout),"Create pipeline layout");
            VkShaderModuleCreateInfo sm{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};sm.codeSize=codes[i].size()*4;sm.pCode=codes[i].data();
            VkShaderModule shader=VK_NULL_HANDLE;check(CreateShaderModule(device,&sm,nullptr,&shader),"Create shader");
            VkComputePipelineCreateInfo pi{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};pi.stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
            pi.stage.stage=VK_SHADER_STAGE_COMPUTE_BIT;pi.stage.module=shader;pi.stage.pName="main";pi.layout=pass.layout;
            auto status=CreateComputePipelines(device,pipelineCache,1,&pi,nullptr,&pass.pipeline);
            DestroyShaderModule(device,shader,nullptr);check(status,"Create compute pipeline");
            VkDescriptorSetAllocateInfo da{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};da.descriptorPool=model->pool;da.descriptorSetCount=1;da.pSetLayouts=&pass.setLayout;
            check(AllocateDescriptorSets(device,&da,&pass.set),"Allocate descriptors");
            std::vector<VkDescriptorImageInfo> info(n+1);
            std::vector<VkWriteDescriptorSet> writes(n+1);
            for(uint32_t b=0;b<=n;b++){
                info[b]={b==n?VK_NULL_HANDLE:sampler,model->images[b==n?pass.output:pass.inputs[b]].view,VK_IMAGE_LAYOUT_GENERAL};
                writes[b]={VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};writes[b].dstSet=pass.set;writes[b].dstBinding=b;writes[b].descriptorCount=1;
                writes[b].descriptorType=bindings[b].descriptorType;writes[b].pImageInfo=&info[b];
            }
            UpdateDescriptorSets(device,n+1,writes.data(),0,nullptr);
        }
        models.emplace(key,std::move(model));
    }
    void barrier(VkImage image,VkAccessFlags source,VkAccessFlags target,VkPipelineStageFlags from,VkPipelineStageFlags to,VkImageLayout old=VK_IMAGE_LAYOUT_GENERAL) {
        VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};b.srcAccessMask=source;b.dstAccessMask=target;b.oldLayout=old;b.newLayout=VK_IMAGE_LAYOUT_GENERAL;
        b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;b.image=image;b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        CmdPipelineBarrier(command,from,to,0,0,nullptr,0,nullptr,1,&b);
    }
    void tile(int key,const uint8_t* input,int w,int h,uint8_t* output,int x,int y) {
        require(input&&output&&w>0&&h>0&&uint64_t(w)*h<=4194304&&x>=0&&x<w&&y>=0&&y<h,"Invalid image or tile");
        auto found=models.find(key);require(found!=models.end(),"Model is not loaded");auto& model=*found->second;model.used=++clock;
        int coreW=std::min(tile_size,w-x),coreH=std::min(tile_size,h-y),halo=model.halo;
        int tw=coreW+halo*2,th=coreH+halo*2;
        for(int ty=0;ty<th;ty++)for(int tx=0;tx<tw;tx++){
            int sx=std::clamp(x+tx-halo,0,w-1),sy=std::clamp(y+ty-halo,0,h-1);
            std::memcpy(mapped+(size_t(ty)*tw+tx)*4,input+(size_t(sy)*w+sx)*4,4);
        }
        VkMappedMemoryRange host{VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE};host.memory=stagingMemory;host.size=VK_WHOLE_SIZE;
        if(!coherent)check(FlushMappedMemoryRanges(device,1,&host),"Flush staging writes");
        check(ResetCommandBuffer(command,0),"Reset commands");
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        check(BeginCommandBuffer(command,&begin),"Begin commands");
        for(auto& image:model.images)barrier(image.handle,model.initialized?(VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT):0,
            VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT|VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_TRANSFER_WRITE_BIT,
            model.initialized?VK_PIPELINE_STAGE_ALL_COMMANDS_BIT:VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
            model.initialized?VK_IMAGE_LAYOUT_GENERAL:VK_IMAGE_LAYOUT_UNDEFINED);
        VkBufferImageCopy copy{};copy.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};copy.imageExtent={uint32_t(tw),uint32_t(th),1};
        CmdCopyBufferToImage(command,staging,model.images[0].handle,VK_IMAGE_LAYOUT_GENERAL,1,&copy);
        barrier(model.images[0].handle,VK_ACCESS_TRANSFER_WRITE_BIT,VK_ACCESS_SHADER_READ_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        int32_t bounds[6]={tw,th,x-halo,y-halo,w,h};
        for(size_t i=0;i<model.passes.size();i++){
            auto& pass=model.passes[i];bool final=i+1==model.passes.size();
            CmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,pass.pipeline);
            CmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,pass.layout,0,1,&pass.set,0,nullptr);
            CmdPushConstants(command,pass.layout,VK_SHADER_STAGE_COMPUTE_BIT,0,sizeof(bounds),bounds);
            CmdDispatch(command,(tw+7)/8,(th+7)/8,1);
            barrier(model.images[pass.output].handle,VK_ACCESS_SHADER_WRITE_BIT,final?VK_ACCESS_TRANSFER_READ_BIT:VK_ACCESS_SHADER_READ_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,final?VK_PIPELINE_STAGE_TRANSFER_BIT:VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        }
        copy.bufferOffset=transferBytes;
        CmdCopyImageToBuffer(command,model.images.back().handle,VK_IMAGE_LAYOUT_GENERAL,staging,1,&copy);
        VkMemoryBarrier read{VK_STRUCTURE_TYPE_MEMORY_BARRIER};read.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;read.dstAccessMask=VK_ACCESS_HOST_READ_BIT;
        CmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,1,&read,0,nullptr,0,nullptr);
        check(EndCommandBuffer(command),"End commands");check(ResetFences(device,1,&fence),"Reset fence");
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&command;
        check(QueueSubmit(queue,1,&submit,fence),"Submit tile");
        check(WaitForFences(device,1,&fence,VK_TRUE,30'000'000'000ULL),"Wait for tile");
        model.initialized=true;
        if(!coherent)check(InvalidateMappedMemoryRanges(device,1,&host),"Invalidate readback");
        for(int row=0;row<coreH;row++)std::memcpy(output+(size_t(y+row)*w+x)*4,
            mapped+transferBytes+(size_t(row+halo)*tw+halo)*4,size_t(coreW)*4);
    }
    void shutdown() noexcept {
        if(device && DeviceWaitIdle)DeviceWaitIdle(device);
        models.clear();
        if(device){
            if(mapped && UnmapMemory)UnmapMemory(device,stagingMemory);
            if(staging && DestroyBuffer)DestroyBuffer(device,staging,nullptr);
            if(stagingMemory && FreeMemory)FreeMemory(device,stagingMemory,nullptr);
            if(pipelineCache && DestroyPipelineCache)DestroyPipelineCache(device,pipelineCache,nullptr);
            if(sampler && DestroySampler)DestroySampler(device,sampler,nullptr);
            if(fence && DestroyFence)DestroyFence(device,fence,nullptr);
            if(commandPool && DestroyCommandPool)DestroyCommandPool(device,commandPool,nullptr);
            if(DestroyDevice)DestroyDevice(device,nullptr);
        }
        device=VK_NULL_HANDLE;
        if(instance && DestroyInstance)DestroyInstance(instance,nullptr);
        instance=VK_NULL_HANDLE;
#ifdef _WIN32
        if(library)FreeLibrary(library);
#else
        if(library)dlclose(library);
#endif
        library=nullptr;
    }
};
Engine::Engine(bool allowSoftware):p(std::make_unique<Impl>(allowSoftware)){}
Engine::~Engine()=default;
std::string Engine::deviceName()const{return p->properties.deviceName;}
void Engine::load(int key,const uint8_t* bytes,size_t length){p->load(key,bytes,length);}
void Engine::tile(int key,const uint8_t* bytes,int w,int h,uint8_t* output,int x,int y){p->tile(key,bytes,w,h,output,x,y);}
}
