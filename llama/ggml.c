// Simplified GGML implementation for Android
// This provides the minimal GGML API needed for llama.cpp compilation

#include "ggml.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>

// GGML context
struct ggml_context {
    size_t mem_size;
    size_t mem_used;
    void * mem_buffer;
    bool   mem_buffer_owned;
    bool   no_alloc;
};

// GGML tensor (basic structure)
struct ggml_tensor {
    enum ggml_type type;
    int    n_dims;
    int64_t ne[4];
    size_t  nb[4];
    void  * data;
    char   name[64];
    enum ggml_op op;
    int32_t op_params[16];
    int     perf_runs;
    int64_t perf_cycles;
    int64_t perf_time_us;
    struct ggml_tensor * view_src;
    size_t view_offset;
    struct ggml_tensor * grad;
    struct ggml_tensor * src[8];  // GGML_MAX_SRC;
    struct ggml_tensor * parent;
};

// GGML compute graph
struct ggml_cgraph {
    int n_nodes;
    int n_leafs;
    int n_threads;
    struct ggml_tensor * nodes[4096];  // GGML_MAX_NODES
    struct ggml_tensor * grads[4096];  // GGML_MAX_NODES
    struct ggml_tensor * leafs[4096];  // GGML_MAX_NODES
};

// GGML backend CPU
struct ggml_backend_cpu {
    struct ggml_backend base;
    int n_threads;
};

// GGML backend buffer
struct ggml_backend_buffer {
    void * data;
    size_t size;
    size_t alignment;
};

// GGML allocator
struct ggml_allocr {
    void * addr;
    size_t size;
    size_t used;
    struct ggml_cgraph * graph;
};

// Element size lookup table
static const size_t GGML_TYPE_SIZE[GGML_TYPE_COUNT] = {
    [GGML_TYPE_F32]  = sizeof(float),
    [GGML_TYPE_F16]  = sizeof(int16_t),
    [GGML_TYPE_Q4_0] = sizeof(int8_t) + sizeof(float),
    [GGML_TYPE_Q4_1] = sizeof(int8_t) + 2 * sizeof(float),
    [GGML_TYPE_Q5_0] = sizeof(int8_t) + sizeof(float) + sizeof(int32_t) / 2,
    [GGML_TYPE_Q5_1] = sizeof(int8_t) + 2 * sizeof(float) + sizeof(int32_t) / 2,
    [GGML_TYPE_Q8_0] = sizeof(int8_t) + sizeof(float),
    [GGML_TYPE_Q8_1] = sizeof(int8_t) + 2 * sizeof(float),
};

GGML_API int ggml_type_size(enum ggml_type type) {
    if (type < 0 || type >= GGML_TYPE_COUNT) {
        return 0;
    }
    return (int)GGML_TYPE_SIZE[type];
}

GGML_API float ggml_type_sizef(enum ggml_type type) {
    return (float)ggml_type_size(type) / sizeof(float);
}

// Context initialization
GGML_API ggml_context * ggml_init(ggml_init_params params) {
    ggml_context * ctx = new ggml_context();
    
    ctx->no_alloc = params.no_alloc;
    
    if (params.mem_size > 0) {
        if (params.mem_buffer == nullptr) {
            ctx->mem_buffer = malloc(params.mem_size);
            ctx->mem_buffer_owned = true;
        } else {
            ctx->mem_buffer = params.mem_buffer;
            ctx->mem_buffer_owned = false;
        }
        ctx->mem_size = params.mem_size;
        ctx->mem_used = 0;
    } else {
        ctx->mem_buffer = nullptr;
        ctx->mem_size = 0;
        ctx->mem_used = 0;
    }
    
    return ctx;
}

// Free context
GGML_API void ggml_free(ggml_context * ctx) {
    if (ctx) {
        if (ctx->mem_buffer_owned && ctx->mem_buffer) {
            free(ctx->mem_buffer);
        }
        delete ctx;
    }
}

// Memory functions
GGML_API size_t ggml_used_mem(const ggml_context * ctx) {
    return ctx ? ctx->mem_used : 0;
}

GGML_API size_t ggml_mem_size(const ggml_context * ctx) {
    return ctx ? ctx->mem_size : 0;
}

// Tensor overhead
GGML_API size_t ggml_tensor_overhead(void) {
    return sizeof(struct ggml_tensor);
}

// Number of elements
GGML_API size_t ggml_nelements(const struct ggml_tensor * tensor) {
    if (!tensor) return 0;
    size_t nelements = 1;
    for (int i = 0; i < tensor->n_dims; i++) {
        nelements *= tensor->ne[i];
    }
    return nelements;
}

// Number of bytes
GGML_API size_t ggml_nbytes(const struct ggml_tensor * tensor) {
    if (!tensor) return 0;
    return ggml_nelements(tensor) * ggml_type_size(tensor->type);
}

GGML_API size_t ggml_nbytes_pad(const struct ggml_tensor * tensor) {
    size_t nbytes = ggml_nbytes(tensor);
    // Align to GGML_MEM_ALIGN
    return (nbytes + GGML_MEM_ALIGN - 1) & ~(GGML_MEM_ALIGN - 1);
}

// Helper to allocate tensor data
static void * ggmlalloc(ggml_context * ctx, size_t size) {
    if (!ctx) return nullptr;
    
    size = (size + GGML_MEM_ALIGN - 1) & ~(GGML_MEM_ALIGN - 1);
    
    if (ctx->mem_buffer == nullptr) {
        return malloc(size);
    }
    
    if (ctx->mem_used + size > ctx->mem_size) {
        return nullptr; // Out of memory
    }
    
    void * result = (char *)ctx->mem_buffer + ctx->mem_used;
    ctx->mem_used += size;
    return result;
}

// Tensor creation
GGML_API struct ggml_tensor * ggml_new_tensor(
        struct ggml_context * ctx,
        enum ggml_type type,
        int n_dims,
        const int64_t * ne) {
    
    struct ggml_tensor * tensor = new ggml_tensor();
    
    tensor->type = type;
    tensor->n_dims = n_dims;
    
    size_t nb = 1;
    for (int i = 0; i < n_dims; i++) {
        tensor->ne[i] = ne[i];
        tensor->nb[i] = nb;
        nb *= ne[i] * ggml_type_size(type);
    }
    
    for (int i = n_dims; i < 4; i++) {
        tensor->ne[i] = 1;
        tensor->nb[i] = nb;
    }
    
    tensor->data = ggmlalloc(ctx, ggml_nbytes(tensor));
    tensor->op = (enum ggml_op)0;
    tensor->view_src = nullptr;
    tensor->view_offset = 0;
    tensor->grad = nullptr;
    for (int i = 0; i < 8; i++) {
        tensor->src[i] = nullptr;
    }
    
    return tensor;
}

GGML_API struct ggml_tensor * ggml_new_tensor_1d(
        struct ggml_context * ctx,
        enum ggml_type type,
        int64_t ne0) {
    return ggml_new_tensor(ctx, type, 1, &ne0);
}

GGML_API struct ggml_tensor * ggml_new_tensor_2d(
        struct ggml_context * ctx,
        enum ggml_type type,
        int64_t ne0,
        int64_t ne1) {
    int64_t ne[2] = {ne0, ne1};
    return ggml_new_tensor(ctx, type, 2, ne);
}

GGML_API struct ggml_tensor * ggml_new_tensor_3d(
        struct ggml_context * ctx,
        enum ggml_type type,
        int64_t ne0,
        int64_t ne1,
        int64_t ne2) {
    int64_t ne[3] = {ne0, ne1, ne2};
    return ggml_new_tensor(ctx, type, 3, ne);
}

GGML_API struct ggml_tensor * ggml_new_tensor_4d(
        struct ggml_context * ctx,
        enum ggml_type type,
        int64_t ne0,
        int64_t ne1,
        int64_t ne2,
        int64_t ne3) {
    int64_t ne[4] = {ne0, ne1, ne2, ne3};
    return ggml_new_tensor(ctx, type, 4, ne);
}

GGML_API struct ggml_tensor * ggml_create_tensor(
        struct ggml_context * ctx,
        enum ggml_type type,
        int n_dims,
        const int64_t * ne) {
    return ggml_new_tensor(ctx, type, n_dims, ne);
}

// Set tensor data
GGML_API void ggml_set_zero(struct ggml_tensor * tensor) {
    if (tensor && tensor->data) {
        memset(tensor->data, 0, ggml_nbytes(tensor));
    }
}

GGML_API void ggml_set_f32(struct ggml_tensor * tensor, const float * data) {
    if (tensor && tensor->data && data && tensor->type == GGML_TYPE_F32) {
        size_t n = ggml_nelements(tensor);
        memcpy(tensor->data, data, n * sizeof(float));
    }
}

GGML_API void ggml_set_i32(struct ggml_tensor * tensor, const int32_t * data) {
    if (tensor && tensor->data && data && tensor->type == GGML_TYPE_I32) {
        size_t n = ggml_nelements(tensor);
        memcpy(tensor->data, data, n * sizeof(int32_t));
    }
}

// Get tensor data
GGML_API void * ggml_get_data(const struct ggml_tensor * tensor) {
    return tensor ? tensor->data : nullptr;
}

GGML_API float * ggml_get_data_f32(const struct ggml_tensor * tensor) {
    if (!tensor || !tensor->data || tensor->type != GGML_TYPE_F32) {
        return nullptr;
    }
    return (float *)tensor->data;
}

// View tensor
GGML_API struct ggml_tensor * ggml_view_tensor(
        struct ggml_context * ctx,
        struct ggml_tensor * src) {
    
    struct ggml_tensor * tensor = new ggml_tensor();
    
    tensor->type = src->type;
    tensor->n_dims = src->n_dims;
    for (int i = 0; i < 4; i++) {
        tensor->ne[i] = src->ne[i];
        tensor->nb[i] = src->nb[i];
    }
    tensor->data = src->data;
    tensor->view_src = src;
    tensor->view_offset = 0;
    
    return tensor;
}

// Graph functions
GGML_API void ggml_build_forward_expand(struct ggml_cgraph * cgraph, struct ggml_tensor * tensor) {
    if (!cgraph || !tensor) return;
    
    if (cgraph->n_nodes < 4096) {
        cgraph->nodes[cgraph->n_nodes++] = tensor;
    }
}

GGML_API void ggml_build_backward_expand(struct ggml_context * ctx, struct ggml_cgraph * cgraph, bool accumulate) {
    // Placeholder for backward pass
}

GGML_API void ggml_graph_compute(struct ggml_context * ctx, struct ggml_cgraph * cgraph) {
    // Placeholder - in real implementation, this would compute the forward pass
    // For now, just mark all nodes as computed
    for (int i = 0; i < cgraph->n_nodes; i++) {
        // Nothing to do - tensors are already allocated
    }
}

GGML_API void ggml_graph_reset(struct ggml_cgraph * cgraph) {
    if (cgraph) {
        cgraph->n_nodes = 0;
        cgraph->n_leafs = 0;
    }
}

GGML_API void ggml_graph_clear(struct ggml_cgraph * cgraph) {
    ggml_graph_reset(cgraph);
}

// Backend CPU
static const char * ggml_backend_cpu_name(ggml_backend * backend) {
    return "CPU";
}

static void ggml_backend_cpu_compute(ggml_backend * backend, struct ggml_cgraph * cgraph) {
    // Placeholder - in real implementation, this would run the compute graph on CPU
    ggml_graph_compute(nullptr, cgraph);
}

static void ggml_backend_cpu_free(ggml_backend * backend) {
    delete backend;
}

GGML_API ggml_backend * ggml_backend_cpu_init(void) {
    ggml_backend_cpu * cpu = new ggml_backend_cpu();
    return (ggml_backend *)cpu;
}

GGML_API void ggml_backend_free(ggml_backend * backend) {
    if (backend) {
        delete backend;
    }
}

GGML_API const char * ggml_backend_name(ggml_backend * backend) {
    return backend ? "Unknown" : "Null";
}

// Backend buffer
GGML_API ggml_backend_buffer * ggml_backend_cpu_buffer_from_ptr(void * ptr, size_t size) {
    ggml_backend_buffer * buf = new ggml_backend_buffer();
    buf->data = ptr;
    buf->size = size;
    buf->alignment = GGML_MEM_ALIGN;
    return buf;
}

// Allocator
GGML_API ggml_allocr * ggml_allocr_new(void * addr, size_t size) {
    ggml_allocr * alloc = new ggml_allocr();
    alloc->addr = addr;
    alloc->size = size;
    alloc->used = 0;
    alloc->graph = nullptr;
    return alloc;
}

GGML_API void ggml_allocr_free(ggml_allocr * alloc) {
    delete alloc;
}

GGML_API void ggml_allocr_reset(ggml_allocr * alloc) {
    if (alloc) {
        alloc->used = 0;
    }
}

GGML_API bool ggml_allocr_alloc(ggml_allocr * alloc, struct ggml_tensor * tensor) {
    if (!alloc || !tensor) return false;
    
    size_t size = ggml_nbytes_pad(tensor);
    if (alloc->used + size > alloc->size) {
        return false;
    }
    
    tensor->data = (char *)alloc->addr + alloc->used;
    alloc->used += size;
    return true;
}

GGML_API void ggml_allocr_set_graph(ggml_allocr * alloc, struct ggml_cgraph * graph) {
    if (alloc) {
        alloc->graph = graph;
    }
}

// Print functions
GGML_API void ggml_print_objects(const struct ggml_context * ctx) {
    // Placeholder
}

GGML_API void ggml_print_tensor(const struct ggml_tensor * tensor) {
    if (!tensor) return;
    
    printf("tensor: name=%s type=%d n_dims=%d ne=[%lld, %lld, %lld, %lld]\n",
           tensor->name, tensor->type, tensor->n_dims,
           (long long)tensor->ne[0], (long long)tensor->ne[1],
           (long long)tensor->ne[2], (long long)tensor->ne[3]);
}
