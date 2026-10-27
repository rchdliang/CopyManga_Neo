#pragma once
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
namespace restore {
constexpr int tile_size = 256;
class Engine {
    struct Impl;
    std::unique_ptr<Impl> p;
public:
    explicit Engine(bool allowSoftware = false);
    ~Engine();
    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;
    std::string deviceName() const;
    void load(int key, const uint8_t* bytes, size_t length);
    void tile(int key, const uint8_t* rgba, int width, int height, uint8_t* output, int x, int y);
};
}
