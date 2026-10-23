#pragma once
#include <string>

// Only invoked in a disposable worker process, never the UI process.
// probe returns a tested GPU index, or -1. An explicit GPU job never silently uses CPU.
int sr_probe(const std::string& models, bool test_gpu);
int sr_run(const std::string& models, int gpu, const std::string& input,
           const std::string& output);
