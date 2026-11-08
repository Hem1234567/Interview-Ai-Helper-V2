# Native layer: llama.cpp (LLM) + whisper.cpp (speech-to-text) sharing ONE ggml build.
# Sources are pinned (tag + commit) and fetched by scripts/fetch-native-deps.sh into
# app/src/main/cpp/third_party. If that folder is empty, this file clones the same pins itself.
cmake_minimum_required(VERSION 3.22.1)
project(copilot_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# NOTE: Do NOT set CMAKE_CXX_VISIBILITY_PRESET / CMAKE_C_VISIBILITY_PRESET here globally.
# Setting them before add_subdirectory() propagates into llama.cpp / whisper.cpp and hides all
# their symbols in the resulting .so files. With Android's --no-undefined linker flag that
# causes "undefined symbol" errors for llama-common's chat-template functions at link time.
# Visibility is scoped to copilot_native only via set_target_properties() below.

set(LLAMA_PIN_TAG    "b10108"  CACHE STRING "llama.cpp tag")
set(LLAMA_PIN_COMMIT "0a50d9909a3478e82679f505bf8595d1eee4b0a8" CACHE STRING "llama.cpp commit")
set(WHISPER_PIN_TAG    "v1.9.5" CACHE STRING "whisper.cpp tag")
set(WHISPER_PIN_COMMIT "d1be6fde11ac6e0407606b4e42fe72d34add8037" CACHE STRING "whisper.cpp commit")
set(COPILOT_THIRD_PARTY "${CMAKE_CURRENT_SOURCE_DIR}/third_party" CACHE PATH "Pinned third-party sources")
# ON for desktop test builds: no dynamic CPU variants, no Android-only libs.
option(COPILOT_HOST_BUILD "Build for the host machine (CI smoke test)" OFF)

function(copilot_fetch NAME URL TAG COMMIT)
  set(DIR "${COPILOT_THIRD_PARTY}/${NAME}")
  if(NOT EXISTS "${DIR}/CMakeLists.txt")
    find_package(Git REQUIRED)
    message(STATUS "Fetching ${NAME} ${TAG}")
    file(MAKE_DIRECTORY "${COPILOT_THIRD_PARTY}")
    execute_process(COMMAND ${GIT_EXECUTABLE} clone --quiet --depth 1 --branch ${TAG} ${URL} "${DIR}"
                    RESULT_VARIABLE rc)
    if(NOT rc EQUAL 0)
      message(FATAL_ERROR "Could not clone ${URL} at ${TAG}")
    endif()
  endif()
  execute_process(COMMAND ${GIT_EXECUTABLE} -C "${DIR}" rev-parse HEAD
                  OUTPUT_VARIABLE HEAD_SHA OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
  if(HEAD_SHA AND NOT HEAD_SHA STREQUAL "${COMMIT}")
    message(FATAL_ERROR "${NAME} is at ${HEAD_SHA}, expected pinned ${COMMIT}. Delete ${DIR} and rebuild.")
  endif()
endfunction()

copilot_fetch(llama.cpp   https://github.com/ggml-org/llama.cpp   ${LLAMA_PIN_TAG}   ${LLAMA_PIN_COMMIT})
copilot_fetch(whisper.cpp https://github.com/ggml-org/whisper.cpp ${WHISPER_PIN_TAG} ${WHISPER_PIN_COMMIT})

# ---- shared options -----------------------------------------------------------------------
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_OPENMP     OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE  OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI       OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

if(NOT COPILOT_HOST_BUILD)
  # Android: every CPU feature level (dotprod / i8mm / SVE ...) is built as a plug-in .so and the
  # best one for the running phone is picked at start-up. One APK, fast on every arm64 device.
  set(GGML_BACKEND_DL       ON CACHE BOOL "" FORCE)
  set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif()

add_subdirectory("${COPILOT_THIRD_PARTY}/llama.cpp" llama EXCLUDE_FROM_ALL)
# whisper.cpp re-uses the existing `ggml` target, so there is exactly one ggml in the process.
add_subdirectory("${COPILOT_THIRD_PARTY}/whisper.cpp" whisper EXCLUDE_FROM_ALL)

add_library(copilot_native SHARED native_llm.cpp native_whisper.cpp native_common.h)

# Scope symbol visibility to this library only — do NOT set it globally before add_subdirectory
# or llama-common's symbols will be hidden in its .so and unresolvable at link time.
set_target_properties(copilot_native PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    C_VISIBILITY_PRESET   hidden)

target_include_directories(copilot_native PRIVATE
  "${COPILOT_THIRD_PARTY}/llama.cpp/include"
  "${COPILOT_THIRD_PARTY}/llama.cpp/common"
  "${COPILOT_THIRD_PARTY}/llama.cpp/vendor"
  "${COPILOT_THIRD_PARTY}/llama.cpp/ggml/include"
  "${COPILOT_THIRD_PARTY}/whisper.cpp/include")
target_link_libraries(copilot_native PRIVATE llama llama-common whisper ggml)

if(ANDROID)
  target_link_libraries(copilot_native PRIVATE android log)
elseif(COPILOT_HOST_BUILD)
  if(COPILOT_JNI_INCLUDE)
    target_include_directories(copilot_native PRIVATE ${COPILOT_JNI_INCLUDE})
  else()
    find_package(JNI REQUIRED)
    target_include_directories(copilot_native PRIVATE ${JNI_INCLUDE_DIRS})
  endif()
endif()
