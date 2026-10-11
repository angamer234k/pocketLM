cmake_minimum_required(VERSION 3.22.1)
project("pocketlm")

# Placeholder for llama.cpp integration
# Later we will add:
# add_subdirectory(llama.cpp)
# target_link_libraries(... llama)

add_library(pocketlm SHARED
    pocketlm_jni.cpp
)

find_library(log-lib log)

target_link_libraries(pocketlm
    ${log-lib}
)
