// Simplified GGML header for Android
#ifndef GGML_H
#define GGML_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// GGML_API for cross-platform
#ifdef GGML_STATIC
#define GGML_API
#else
#ifdef GGML_SHARED
#ifdef _WIN32
#define GGML_API __declspec(dllimport)
#else
#define GGML_API __attribute__((visibility("default")))
#endif
#else
#define GGML_API
#endif
#endif

// Memory alignment
#define GGML_MEM_ALIGN 16

// Tensor types
enum ggml_type {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q4_1 = 3,
    GGML_TYPE_Q5_0 = 6,
    GGML_TYPE_Q5_1 = 7,
    GGML_TYPE_Q8_0 = 8,
    GGML_TYPE_Q8_1 = 9,
    GGML_TYPE_Q2_K = 10,
    GGML_TYPE_Q3_K = 11,
    GGML_TYPE_Q4_K = 12,
    GGML_TYPE_Q5_K = 13,
    GGML_TYPE_Q6_K = 14,
    GGML_TYPE_I8   = 15,
    GGML_TYPE_I16  = 16,
    GGML_TYPE_I32  = 17,
    GGML_TYPE_I64  = 18,
    GGML_TYPE_F64  = 19,
    GGML_TYPE_COUNT,
};

// Element size of a type
GGML_API int ggml_type_size(enum ggml_type type);
GGML_API float ggml_type_sizef(enum ggml_type type);

// Context
struct ggml_context;
typedef struct ggml_context ggml_context;

// Tensor
struct ggml_tensor {
    enum ggml_type type;
    int    n_dims;
    int64_t ne[4];         // number of elements per dimension
    size_t  nb[4];         // stride in bytes
    void  * data;
    char   name[64];

    // compute
    enum ggml_op op;

    // op params
    int32_t op_params[16];

    // performance
    int     perf_runs;
    int64_t perf_cycles;
    int64_t perf_time_us;

    struct ggml_tensor * view_src;
    size_t view_offset;

    struct ggml_tensor * grad;
    struct ggml_tensor * src[8];  // GGML_MAX_SRC
    struct ggml_tensor * parent;
    struct ggml_hash_set * view_hash;
    char   padding[8];
};

#define GGML_MAX_DIMS      4
#define GGML_MAX_NAME      64
#define GGML_MAX_PARAMS    32
#define GGML_MAX_NODES     4096
#define GGML_MAX_GRAPH_SIZE 4096

// Compute graph
struct ggml_cgraph {
    int n_nodes;
    int n_leafs;
    int n_threads;

    struct ggml_tensor * nodes[GGML_MAX_NODES];
    struct ggml_tensor * grads[GGML_MAX_NODES];
    struct ggml_tensor * leafs[GGML_MAX_NODES];

    void * visited_hash_table;
};

typedef struct ggml_cgraph ggml_cgraph;

// Context parameters
typedef struct {
    size_t mem_size;
    void * mem_buffer;
    bool   mem_buffer_owned;
    bool   no_alloc;
    bool   no_random_init;
} ggml_init_params;

// Context functions
GGML_API ggml_context * ggml_init(ggml_init_params params);
GGML_API void ggml_free(ggml_context * ctx);

// Memory
GGML_API size_t ggml_used_mem(const ggml_context * ctx);
GGML_API size_t ggml_mem_size(const ggml_context * ctx);

// Tensor creation
GGML_API struct ggml_tensor * ggml_new_tensor(
    struct ggml_context * ctx,
    enum ggml_type type,
    int n_dims,
    const int64_t * ne
);

GGML_API struct ggml_tensor * ggml_new_tensor_1d(
    struct ggml_context * ctx,
    enum ggml_type type,
    int64_t ne0
);

GGML_API struct ggml_tensor * ggml_new_tensor_2d(
    struct ggml_context * ctx,
    enum ggml_type type,
    int64_t ne0,
    int64_t ne1
);

GGML_API struct ggml_tensor * ggml_new_tensor_3d(
    struct ggml_context * ctx,
    enum ggml_type type,
    int64_t ne0,
    int64_t ne1,
    int64_t ne2
);

GGML_API struct ggml_tensor * ggml_new_tensor_4d(
    struct ggml_context * ctx,
    enum ggml_type type,
    int64_t ne0,
    int64_t ne1,
    int64_t ne2,
    int64_t ne3
);

GGML_API struct ggml_tensor * ggml_create_tensor(
    struct ggml_context * ctx,
    enum ggml_type type,
    int n_dims,
    const int64_t * ne
);

// Set tensor data
GGML_API void ggml_set_zero(struct ggml_tensor * tensor);
GGML_API void ggml_set_i8 (struct ggml_tensor * tensor, const int8_t  * data);
GGML_API void ggml_set_i16(struct ggml_tensor * tensor, const int16_t * data);
GGML_API void ggml_set_i32(struct ggml_tensor * tensor, const int32_t * data);
GGML_API void ggml_set_i64(struct ggml_tensor * tensor, const int64_t * data);
GGML_API void ggml_set_f16(struct ggml_tensor * tensor, const int16_t * data);
GGML_API void ggml_set_f32(struct ggml_tensor * tensor, const float  * data);
GGML_API void ggml_set_f64(struct ggml_tensor * tensor, const double * data);

// Get tensor data
GGML_API void * ggml_get_data(const struct ggml_tensor * tensor);
GGML_API float * ggml_get_data_f32(const struct ggml_tensor * tensor);

// Tensor operations
GGML_API struct ggml_tensor * ggml_view_tensor(
    struct ggml_context * ctx,
    struct ggml_tensor * src
);

// Graph computation
GGML_API void ggml_build_forward_expand(struct ggml_cgraph * cgraph, struct ggml_tensor * tensor);
GGML_API void ggml_build_backward_expand(struct ggml_context * ctx, struct ggml_cgraph * cgraph, bool accumulate);
GGML_API void ggml_graph_compute(struct ggml_context * ctx, struct ggml_cgraph * cgraph);
GGML_API void ggml_graph_reset(struct ggml_cgraph * cgraph);

// Graph cleanup
GGML_API void ggml_graph_clear(struct ggml_cgraph * cgraph);

// Backend (CPU)
struct ggml_backend;
typedef struct ggml_backend ggml_backend;

GGML_API ggml_backend * ggml_backend_cpu_init(void);
GGML_API void ggml_backend_free(ggml_backend * backend);
GGML_API const char * ggml_backend_name(ggml_backend * backend);
GGML_API void ggml_backend_compute(ggml_backend * backend, struct ggml_cgraph * cgraph);

// Backend buffer
struct ggml_backend_buffer;
typedef struct ggml_backend_buffer ggml_backend_buffer;

GGML_API ggml_backend_buffer * ggml_backend_cpu_buffer_from_ptr(void * ptr, size_t size);

// Allocator
struct ggml_allocr;
typedef struct ggml_allocr ggml_allocr;

GGML_API ggml_allocr * ggml_allocr_new(void * addr, size_t size);
GGML_API void ggml_allocr_free(ggml_allocr * alloc);
GGML_API void ggml_allocr_reset(ggml_allocr * alloc);
GGML_API bool ggml_allocr_alloc(ggml_allocr * alloc, struct ggml_tensor * tensor);
GGML_API void ggml_allocr_set_graph(ggml_allocr * alloc, struct ggml_cgraph * graph);

// Alignment
GGML_API size_t ggml_tensor_overhead(void);
GGML_API size_t ggml_nelements(const struct ggml_tensor * tensor);
GGML_API size_t ggml_nbytes(const struct ggml_tensor * tensor);
GGML_API size_t ggml_nbytes_pad(const struct ggml_tensor * tensor);

// Print
GGML_API void ggml_print_objects(const struct ggml_context * ctx);
GGML_API void ggml_print_tensor(const struct ggml_tensor * tensor);

#ifdef __cplusplus
}
#endif

#endif // GGML_H
