cmake_minimum_required(VERSION 3.18.1)
project(nox CXX)
set(CMAKE_CXX_STANDARD 17)
add_library(nox SHARED nox.cpp)
# Symboles cachés + optimisation : le code natif est plus dur à analyser
target_compile_options(nox PRIVATE -O2 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections)
target_link_options(nox PRIVATE -Wl,--gc-sections -s)
