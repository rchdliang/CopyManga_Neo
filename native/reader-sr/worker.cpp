#include "engine.h"
#include <cstdio>
#include <cstdlib>
#include <exception>
#include <string>
#include <vector>
#include <filesystem>

static int worker_main(int argc, char** argv) {
    try {
        if (argc == 4 && std::string(argv[1]) == "probe") {
            const int device = sr_probe(argv[2], std::string(argv[3]) == "gpu");
            if (device < -1) return 10;
            std::printf("CMP_SR_1 %d\n", device);
            return 0;
        }
        if (argc == 6 && std::string(argv[1]) == "run") {
            size_t consumed = 0; const int gpu = std::stoi(argv[3], &consumed);
            if (consumed != std::string(argv[3]).size()) return 11;
            return sr_run(argv[2], gpu, argv[4], argv[5]);
        }
    } catch (const std::exception&) { return 12; }
    return 11;
}

#ifdef _WIN32
int wmain(int argc, wchar_t** argv) {
    std::vector<std::string> utf8;
    for (int i = 0; i < argc; i++) utf8.push_back(std::filesystem::path(argv[i]).u8string());
    std::vector<char*> arguments;
    for (auto& value : utf8) arguments.push_back(value.data());
    return worker_main(argc, arguments.data());
}
#else
int main(int argc, char** argv) { return worker_main(argc, argv); }
#endif
