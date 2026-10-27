#include "engine.h"
#include <fstream>
#include <iostream>
#include <vector>
#include <chrono>
#include <stdexcept>
int main(int argc,char** argv) {
    try {
        if(argc<6){std::cerr<<"usage: restore-smoke model.akv input.rgba width height output.rgba [--allow-software]\n";return 2;}
        int w=std::stoi(argv[3]),h=std::stoi(argv[4]);
        if(w<=0||h<=0||int64_t(w)*h>4194304)throw std::runtime_error("Invalid size");
        std::ifstream modelFile(argv[1],std::ios::binary);if(!modelFile)throw std::runtime_error("Model missing");
        std::vector<uint8_t> model((std::istreambuf_iterator<char>(modelFile)),{});
        std::vector<uint8_t> input(size_t(w)*h*4),output(input.size());
        std::ifstream source(argv[2],std::ios::binary);
        if(!source.read(reinterpret_cast<char*>(input.data()),input.size()))throw std::runtime_error("Input incomplete");
        restore::Engine engine(argc>6 && std::string(argv[6])=="--allow-software");
        auto start=std::chrono::steady_clock::now();
        engine.load(0,model.data(),model.size());
        for(int y=0;y<h;y+=restore::tile_size)for(int x=0;x<w;x+=restore::tile_size)engine.tile(0,input.data(),w,h,output.data(),x,y);
        auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count();
        std::ofstream out(argv[5],std::ios::binary);out.write(reinterpret_cast<char*>(output.data()),output.size());
        if(!out)throw std::runtime_error("Output failed");
        std::cout<<engine.deviceName()<<" "<<w<<"x"<<h<<" "<<ms<<" ms\n";
        return 0;
    }catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}
}
