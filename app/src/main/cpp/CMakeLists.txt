cmake_minimum_required(VERSION 3.22.1)
project(stitcher_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    stitcher_native
    SHARED
    NativeStitcher.cpp
)

find_package(OpenCV REQUIRED COMPONENTS core imgproc stitching)

target_link_libraries(
    stitcher_native
    ${OpenCV_LIBS}
)
