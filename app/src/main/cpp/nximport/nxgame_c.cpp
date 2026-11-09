#include "nxgame_c.h"

#include "nxgame.h"

#include <algorithm>
#include <cstring>
#include <exception>
#include <memory>
#include <new>
#include <string>
#include <vector>

struct nxi_game_image {
    std::unique_ptr<nxi::GameImage> image;
};

namespace {
void set_error(char* destination, size_t capacity, const std::string& message) noexcept {
    if (!destination || capacity == 0) return;
    const size_t count = std::min(capacity - 1, message.size());
    if (count) std::memcpy(destination, message.data(), count);
    destination[count] = '\0';
}

void clear_error(char* destination, size_t capacity) noexcept {
    if (destination && capacity) destination[0] = '\0';
}
}  // namespace

extern "C" {

nxi_game_image* nxi_game_open(const nxi_game_input* inputs, size_t input_count, uint64_t title_id,
                              char* error, size_t error_capacity) {
    clear_error(error, error_capacity);
    try {
        if (!inputs || input_count == 0) {
            set_error(error, error_capacity, "no package inputs were supplied");
            return nullptr;
        }
        std::vector<nxi::InputFile> files;
        files.reserve(input_count);
        for (size_t i = 0; i < input_count; ++i) {
            if (!inputs[i].name || inputs[i].fd < 0) {
                set_error(error, error_capacity, "invalid package input name or file descriptor");
                return nullptr;
            }
            files.push_back({inputs[i].name, inputs[i].fd});
        }
        std::string detail;
        auto image = nxi::GameImage::open(files, title_id, detail);
        if (!image) {
            set_error(error, error_capacity, detail.empty() ? "could not open game image" : detail);
            return nullptr;
        }
        auto* handle = new (std::nothrow) nxi_game_image;
        if (!handle) {
            set_error(error, error_capacity, "out of memory allocating game-image handle");
            return nullptr;
        }
        handle->image = std::move(image);
        return handle;
    } catch (const std::exception& exception) {
        set_error(error, error_capacity, exception.what());
    } catch (...) {
        set_error(error, error_capacity, "unknown native exception opening game image");
    }
    return nullptr;
}

void nxi_game_close(nxi_game_image* image) { delete image; }

uint64_t nxi_game_title_id(const nxi_game_image* image) {
    return image && image->image ? image->image->title_id() : 0;
}

size_t nxi_game_file_count(const nxi_game_image* image) {
    return image && image->image ? image->image->file_count() : 0;
}

int nxi_game_stat(const nxi_game_image* image, const char* path, nxi_game_entry* entry) {
    if (!image || !image->image || !path || !entry) return -1;
    const auto* found = image->image->find(path);
    if (!found) return 0;
    entry->size = found->size;
    entry->is_directory = found->dir ? 1U : 0U;
    return 1;
}

size_t nxi_game_list_count(const nxi_game_image* image, const char* directory) {
    if (!image || !image->image || !directory) return 0;
    return image->image->list(directory).size();
}

int nxi_game_list_name(const nxi_game_image* image, const char* directory, size_t index,
                       char* name, size_t name_capacity, nxi_game_entry* entry) {
    if (!image || !image->image || !directory || !name || name_capacity == 0 || !entry) return -1;
    const auto children = image->image->list(directory);
    if (index >= children.size()) return -1;
    const auto& child = children[index];
    if (child.first.size() >= name_capacity) return -1;
    std::memcpy(name, child.first.data(), child.first.size());
    name[child.first.size()] = '\0';
    entry->size = child.second.size;
    entry->is_directory = child.second.dir ? 1U : 0U;
    return 0;
}

uint64_t nxi_game_read(const nxi_game_image* image, const char* path, uint64_t offset,
                       void* destination, uint64_t length, char* error, size_t error_capacity) {
    clear_error(error, error_capacity);
    if (!image || !image->image || !path) {
        set_error(error, error_capacity, "invalid game-image handle or path");
        return 0;
    }
    if (length == 0) return 0;
    if (!destination) {
        set_error(error, error_capacity, "null destination for non-empty game-image read");
        return 0;
    }
    const auto* found = image->image->find(path);
    if (!found || found->dir || offset >= found->size) return 0;
    try {
        std::string detail;
        const uint64_t count = image->image->read(*found, offset, destination, length, detail);
        if (!detail.empty()) set_error(error, error_capacity, detail);
        return count;
    } catch (const std::exception& exception) {
        set_error(error, error_capacity, exception.what());
    } catch (...) {
        set_error(error, error_capacity, "unknown native exception reading game image");
    }
    return 0;
}

}  // extern "C"
