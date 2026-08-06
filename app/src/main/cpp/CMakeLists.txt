cmake_minimum_required(VERSION 3.22.1)
project("gittool_security")

add_library(gittool_security SHARED native-lib.cpp)
find_library(log-lib log)
target_link_libraries(gittool_security ${log-lib})
