// Simplified GGML allocator implementation for Android

#include "ggml.h"

#include <cstdlib>
#include <cstring>

// GGML allocator
struct ggml_allocr {
    void * addr;
    size_t size;
    size_t used;
    struct ggml_cgraph * graph;
    int n_allocations;
};

// Hash set for tensor tracking
struct ggml_hash_set {
    // Simplified - just use a fixed size for now
    int size;
    int n;
};

static struct ggml_hash_set ggml_hash_set_new(int size) {
    struct ggml_hash_set set;
    set.size = size;
    set.n = 0;
    return set;
}

// Memory pool structure
struct ggml_allocr {
    void * addr;
    size_t size;
    size_t used;
    struct ggml_cgraph * graph;
    int n_allocations;
};

GGML_API ggml_allocr * ggml_allocr_new(void * addr, size_t size) {
    ggml_allocr * alloc = new ggml_allocr();
    alloc->addr = addr;
    alloc->size = size;
    alloc->used = 0;
    alloc->graph = nullptr;
    alloc->n_allocations = 0;
    return alloc;
}

GGML_API void ggml_allocr_free(ggml_allocr * alloc) {
    delete alloc;
}

GGML_API void ggml_allocr_reset(ggml_allocr * alloc) {
    if (alloc) {
        alloc->used = 0;
        alloc->n_allocations = 0;
    }
}

GGML_API bool ggml_allocr_alloc(ggml_allocr * alloc, struct ggml_tensor * tensor) {
    if (!alloc || !tensor) return false;
    
    // Calculate aligned size
    size_t nbytes = ggml_nbytes_pad(tensor);
    
    // Check if we have enough space
    if (alloc->used + nbytes > alloc->size) {
        return false;
    }
    
    // Allocate from the pool
    tensor->data = (char *)alloc->addr + alloc->used;
    tensor->parent = (struct ggml_tensor *)alloc;
    alloc->used += nbytes;
    alloc->n_allocations++;
    
    return true;
}

GGML_API void ggml_allocr_set_graph(ggml_allocr * alloc, struct ggml_cgraph * graph) {
    if (alloc) {
        alloc->graph = graph;
    }
}

// Additional allocator functions for ggml-alloc.c compatibility
size_t ggml_allocr_used(const ggml_allocr * alloc) {
    return alloc ? alloc->used : 0;
}

size_t ggml_allocr_max_size(const ggml_allocr * alloc) {
    return alloc ? alloc->size : 0;
}

bool ggml_allocr_is_own(ggml_allocr * alloc) {
    return alloc != nullptr;
}
