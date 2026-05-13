// Simplified GGML backend implementation for Android

#include "ggml.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

// Backend CPU implementation
struct ggml_backend {
    const char * name;
    void (*free_fn)(struct ggml_backend *);
    const char * (*name_fn)(struct ggml_backend *);
    void (*compute_fn)(struct ggml_backend *, struct ggml_cgraph *);
    int (*get_alignment_fn)(struct ggml_backend *);
};

static const char * ggml_backend_cpu_name_impl(ggml_backend * backend) {
    return "CPU";
}

static void ggml_backend_cpu_compute_impl(ggml_backend * backend, struct ggml_cgraph * cgraph) {
    // Simplified compute - just iterate through nodes
    for (int i = 0; i < cgraph->n_nodes; i++) {
        struct ggml_tensor * tensor = cgraph->nodes[i];
        // In real implementation, this would execute the operation
    }
}

static void ggml_backend_cpu_free_impl(ggml_backend * backend) {
    delete backend;
}

static int ggml_backend_cpu_get_alignment(ggml_backend * backend) {
    return GGML_MEM_ALIGN;
}

struct ggml_backend * ggml_backend_cpu_init_impl(void) {
    struct ggml_backend * backend = new struct ggml_backend();
    backend->name = "CPU";
    backend->free_fn = ggml_backend_cpu_free_impl;
    backend->name_fn = ggml_backend_cpu_name_impl;
    backend->compute_fn = ggml_backend_cpu_compute_impl;
    backend->get_alignment_fn = ggml_backend_cpu_get_alignment;
    return backend;
}

// Public API implementations
const char * ggml_backend_name(ggml_backend * backend) {
    if (!backend || !backend->name_fn) return "NULL";
    return backend->name_fn(backend);
}

void ggml_backend_free(ggml_backend * backend) {
    if (backend && backend->free_fn) {
        backend->free_fn(backend);
    }
}

void ggml_backend_compute(ggml_backend * backend, struct ggml_cgraph * cgraph) {
    if (backend && backend->compute_fn) {
        backend->compute_fn(backend, cgraph);
    }
}

// Backend buffer implementation
struct ggml_backend_buffer {
    void * data;
    size_t size;
    size_t alignment;
    ggml_backend * backend;
};

ggml_backend_buffer * ggml_backend_cpu_buffer_from_ptr_impl(void * ptr, size_t size) {
    ggml_backend_buffer * buf = new ggml_backend_buffer();
    buf->data = ptr;
    buf->size = size;
    buf->alignment = GGML_MEM_ALIGN;
    buf->backend = nullptr;
    return buf;
}

ggml_backend_buffer * ggml_backend_cpu_buffer_from_ptr(void * ptr, size_t size) {
    return ggml_backend_cpu_buffer_from_ptr_impl(ptr, size);
}
