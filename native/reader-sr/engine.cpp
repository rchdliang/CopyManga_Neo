#include "engine.h"
#include "waifu2x.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <filesystem>
#include <vector>

namespace {
constexpr int kTile = 32;
constexpr int64_t kMaxPixels = 3 * 1024 * 1024;
struct GpuLifetime {
    bool attempted = false;
    ~GpuLifetime() { if (attempted) ncnn::destroy_gpu_instance(); }
    bool create() { attempted = true; return ncnn::create_gpu_instance() == 0; }
};
int load(Waifu2x& engine, const std::string& directory) {
    const auto dir = std::filesystem::u8path(directory);
#ifdef _WIN32
    const int result = engine.load((dir / "scale2.0x_model.param").wstring(), (dir / "scale2.0x_model.bin").wstring());
#else
    const int result = engine.load((dir / "scale2.0x_model.param").string(), (dir / "scale2.0x_model.bin").string());
#endif
    engine.noise = -1; engine.scale = 2; engine.tilesize = kTile; engine.prepadding = 18;
    return result;
}
bool usable(int index) {
    if (index < 0 || index >= ncnn::get_gpu_count()) return false;
    const auto* device = ncnn::get_gpu_device(index);
    return device && device->is_valid() && device->get_heap_budget() >= 128;
}
FILE* open_file(const std::string& path, bool write) {
#ifdef _WIN32
    return _wfopen(std::filesystem::u8path(path).c_str(), write ? L"wb" : L"rb");
#else
    return fopen(path.c_str(), write ? "wb" : "rb");
#endif
}
struct File {
    FILE* p;
    ~File() { if (p) fclose(p); }
};
uint32_t read_u32(FILE* f) {
    unsigned char b[4];
    if (fread(b, 1, 4, f) != 4) return 0;
    return uint32_t(b[0]) | (uint32_t(b[1]) << 8) | (uint32_t(b[2]) << 16) | (uint32_t(b[3]) << 24);
}
bool write_u32(FILE* f, uint32_t x) {
    unsigned char b[] = {static_cast<unsigned char>(x), static_cast<unsigned char>(x >> 8), static_cast<unsigned char>(x >> 16), static_cast<unsigned char>(x >> 24)};
    return fwrite(b, 1, 4, f) == 4;
}
void swap_rb(std::vector<unsigned char>& bytes) {
#ifdef _WIN32
    for (size_t i = 0; i < bytes.size(); i += 4) std::swap(bytes[i], bytes[i + 2]);
#endif
}
}

int sr_probe(const std::string& models, bool test_gpu) {
    // CPU model load and actual inference are checked before advertising any backend.
    constexpr int w = 32, h = 32;
    std::vector<unsigned char> pixels(w * h * 4), reference(w * h * 16), candidate(reference.size());
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
        size_t i = (y * w + x) * 4;
        pixels[i] = (x * 7 + y * 3) % 256; pixels[i+1] = y * 7;
        pixels[i+2] = ((x / 4 + y / 4) % 2) ? 210 : 35; pixels[i+3] = 255;
    }
    ncnn::Mat input(w, h, pixels.data(), size_t(4), 4);
    ncnn::Mat cpu_output(w*2, h*2, reference.data(), size_t(4), 4);
    {
        Waifu2x cpu(-1, false, 1);
        if (load(cpu, models) != 0 || cpu.process(input, cpu_output) != 0) return -2;
    }
    int low = 255, high = 0;
    for (size_t i = 0; i < reference.size(); i++) if (i % 4 != 3) {
        low = std::min(low, int(reference[i])); high = std::max(high, int(reference[i]));
    }
    if (high - low < 32) return -2; // Alpha alone must not make an empty RGB output pass.

    if (!test_gpu) return -1;
    GpuLifetime lifetime;
    if (!lifetime.create()) return -1;
    // Bounded probing; no vendor allow-list. If a driver crashes, the parent observes worker death.
    for (int index = 0; index < std::min(ncnn::get_gpu_count(), 4); index++) {
        if (!usable(index)) continue;
        Waifu2x gpu(index, false, 1);
        ncnn::Mat gpu_output(w*2, h*2, candidate.data(), size_t(4), 4);
        if (load(gpu, models) != 0 || gpu.process(input, gpu_output) != 0) continue;
        double error = 0; int maximum = 0;
        for (size_t i = 0; i < reference.size(); i++) {
            const int delta = std::abs(int(reference[i]) - int(candidate[i]));
            error += delta; maximum = std::max(maximum, delta);
        }
        if (error / reference.size() <= 2.0 && maximum <= 32) return index;
    }
    return -1;
}

int sr_run(const std::string& models, int gpu, const std::string& input_path, const std::string& output_path) {
    File input{open_file(input_path, false)};
    if (!input.p || read_u32(input.p) != 0x31525343) return 2; // CSR1, LE RGBA8
    const uint32_t w = read_u32(input.p), h = read_u32(input.p);
    if (!w || !h || w > 4096 || h > 4096 || int64_t(w) * h > kMaxPixels) return 3;
    std::vector<unsigned char> pixels(size_t(w) * h * 4);
    if (fread(pixels.data(), 1, pixels.size(), input.p) != pixels.size() || fgetc(input.p) != EOF) return 4;
    GpuLifetime lifetime;
    if (gpu >= 0 && (!lifetime.create() || !usable(gpu))) return 5;
    if (gpu < -1) return 5;
    swap_rb(pixels);
    std::vector<unsigned char> output(pixels.size() * 4);
    {
        Waifu2x engine(gpu, false, 1);
        if (load(engine, models) != 0) return 6;
        ncnn::Mat source(w, h, pixels.data(), size_t(4), 4);
        ncnn::Mat target(w*2, h*2, output.data(), size_t(4), 4);
        if (engine.process(source, target) != 0) return 7;
    }
    swap_rb(output);
    File file{open_file(output_path, true)};
    if (!file.p || !write_u32(file.p, 0x31525343) || !write_u32(file.p, w*2) || !write_u32(file.p, h*2)) return 8;
    if (fwrite(output.data(), 1, output.size(), file.p) != output.size() || fflush(file.p) != 0) return 8;
    return 0;
}
