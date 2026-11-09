// Stable C ABI for the in-place NSP/XCI reader. This header is usable from Rust without exposing
// C++ classes, STL containers, or exceptions across the FFI boundary.
#pragma once

#include <stddef.h>
#include <stdint.h>

#if defined(__GNUC__)
#define NXI_GAME_API __attribute__((visibility("default")))
#else
#define NXI_GAME_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct nxi_game_image nxi_game_image;

typedef struct nxi_game_input {
    const char* name;  // UTF-8 basename: package/split part or prod.keys/title.keys
    int fd;            // readable descriptor; the reader duplicates it and does not take ownership
} nxi_game_input;

typedef struct nxi_game_entry {
    uint64_t size;
    uint32_t is_directory;
} nxi_game_entry;

// Returns NULL on failure and writes a bounded, NUL-terminated diagnostic when error_capacity > 0.
// title_id 0 selects the only application title found. The reader duplicates all supplied FDs.
NXI_GAME_API nxi_game_image* nxi_game_open(
    const nxi_game_input* inputs, size_t input_count, uint64_t title_id,
    char* error, size_t error_capacity);

NXI_GAME_API void nxi_game_close(nxi_game_image* image);
NXI_GAME_API uint64_t nxi_game_title_id(const nxi_game_image* image);
NXI_GAME_API size_t nxi_game_file_count(const nxi_game_image* image);

// Returns 1 when path exists (including directories), 0 when it does not, and -1 on invalid arguments.
NXI_GAME_API int nxi_game_stat(
    const nxi_game_image* image, const char* path, nxi_game_entry* entry);

// Immediate child count and indexed child name for directory enumeration. Returns 0 on success,
// -1 for invalid arguments/index; name is always NUL-terminated when capacity is nonzero.
NXI_GAME_API size_t nxi_game_list_count(const nxi_game_image* image, const char* directory);
NXI_GAME_API int nxi_game_list_name(const nxi_game_image* image, const char* directory, size_t index,
                                    char* name, size_t name_capacity, nxi_game_entry* entry);

// Reads up to `length` bytes at `offset`; returns bytes read. A clean EOF returns 0 and clears error.
// A failed read also returns 0, but writes a diagnostic to error. Directory reads return 0.
NXI_GAME_API uint64_t nxi_game_read(
    const nxi_game_image* image, const char* path, uint64_t offset,
    void* destination, uint64_t length, char* error, size_t error_capacity);

#ifdef __cplusplus
}
#endif
