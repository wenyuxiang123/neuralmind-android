// Minimal llama.cpp header for Android JNI integration
// This is a simplified version focusing on CPU inference

#ifndef LLAMA_H
#define LLAMA_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Model architecture types
#define LLAMA_ARCH_LLAMA        "llama"
#define LLAMA_ARCH_FALCON       "falcon"
#define LLAMA_ARCH_BAICHUAN     "baichuan"
#define LLAMA_ARCH_GPT2         "gpt2"
#define LLAMA_ARCH_GROK         "grok"
#define LLAMA_ARCH_DBRX         "dbrx"
#define LLAMA_ARCH_MINICM       "minicm"
#define LLAMA_ARCH_UNKNOWN      "unknown"

// Token type
typedef int32_t llama_token;

// Token data
typedef struct {
    llama_token id;
    llama_token logit;
    float p;
} llama_token_data;

// Model file type
enum llama_ftype {
    LLAMA_FTYPE_ALL_F32     = 0,
    LLAMA_FTYPE_MOSTLY_F16  = 1,
    LLAMA_FTYPE_MOSTLY_Q4_0 = 2,
    LLAMA_FTYPE_MOSTLY_Q4_1 = 3,
    LLAMA_FTYPE_MOSTLY_Q5_0 = 6,
    LLAMA_FTYPE_MOSTLY_Q5_1 = 7,
    LLAMA_FTYPE_MOSTLY_Q8_0 = 8,
    LLAMA_FTYPE_MOSTLY_F16  = 1,
};

// Model hyperparameters
typedef struct {
    uint32_t n_vocab;
    uint32_t n_embd;
    uint32_t n_mult;
    uint32_t n_head;
    uint32_t n_head_kv;
    uint32_t n_layer;
    uint32_t n_rot;
    uint32_t n_ff;
    uint32_t n_expert;
    uint32_t n_expert_used;
    float eps;
    float rope_freq_base;
    float rope_freq_scale;
    float f_norm_eps;
    float f_rms_norm_eps;
    uint32_t n_ctx_train;
    uint32_t n_ctx;
    uint32_t n_orig_ctx;
    uint32_t rope_scaling_type;
    float rope_scaling_factor;
    float rope_norm_scaling_factor;
    float rope_theta;
    float rope_trad_version;
    float partial_rot_factor;
    float cm_scale;
    float tax_scale;
    int32_t rope_no_nan;
    int32_t n_embd_head;
    int32_t n_embd_gqa;
    int32_t n_ctx_per_tkv;
    int32_t token_guessing_score;
    int32_t token_negative;
    int32_t n_thread;
    float logic_dir;
    int32_t n_features;
    int32_t n_rings;
    char arch[64];
} llama_hparams;

// Vocab
typedef struct {
    char * text;
    float score;
    int32_t tail_len;
} llama_vocab_item;

typedef struct {
    const llama_vocab_item * items;
    int32_t n_tokens;
    int32_t max_token_len;
    int32_t sot_id;
    int32_t eot_id;
    int32_t newline_id;
    int32_t eos_id;
    int32_t eom_id;
    int32_t boi_id;
    int32_t eoi_id;
    int32_t prefix_id;
    int32_t middle_id;
    int32_t suffix_id;
    int32_t cache_id;
    int32_t infill_id;
    int32_t placeholder_token_id;
    int32_t mask_token_id;
} llama_vocab;

// Model
struct llama_model;
typedef struct llama_model llama_model;

// Context
struct llama_context;
typedef struct llama_context llama_context;

// Model loading parameters
typedef struct {
    // model path
    const char * path_model;
    
    // model params
    llama_ftype ftype;
    bool kvall;
    
    // Numa options
    bool numa;
    
    // encoding
    int32_t n_ctx;           // context size
    int32_t n_batch;         // batch size for prompt processing
    int32_t n_threads;        // threads for batch processing
    int32_t n_threads_batch;  // threads for batch generation
    
    // model setup
    bool n_gpu_layers;        // number of layers to store in VRAM
    int64_t main_gpu;          // the GPU that is used for scratch and small tensors
    float tensor_split[3];     // how to split layers across multiple GPUs
    
    // RoPE scaling
    float rope_scaling[3];     // scale, original context, alpha value
    
    // attention params
    bool flash_attention;
    bool n_prev_repeat;        // number of tokens to keep in repetition penalty
    float repeat_penalty;
    float frequency_penalty;
    float presence_penalty;
    int32_t mirostat;
    float mirostat_eta;
    float mirostat_tau;
    int32_t eos_token_id;
    int32_t tdz_fill_dist;
    
    // llama models: n_ctx must be defined for all models
    // georgia distil: n_ctx is optional, defaults to 512
} llama_model_params;

llama_model_params llama_model_default_params(void);

// Context parameters
typedef struct {
    int32_t n_ctx;         // context size
    int32_t n_batch;       // batch size for prompt processing
    int32_t n_threads;     // threads for batch processing
    int32_t n_threads_batch; // threads for batch generation
    bool rope_scaling_type;
    float rope_scaling[3];
    float rope_freq_base;
    float rope_freq_scale;
    float yarn_ext_factor;
    float yarn_attn_factor;
    float yarn_beta_fast;
    float yarn_beta_slow;
    float rope_trad_version;
    float partial_rot_factor;
    float use纸张;
    float cm_scale;
    float tax_scale;
    bool flash_attention;
    bool no_perf;
    bool n_ctx_per_tkv;
    bool cache_type_k;
    bool cache_type_v;
} llama_context_params;

llama_context_params llama_context_default_params(void);

// Model functions
llama_model * llama_load_model_from_file(const char * path_model, llama_model_params params);
void llama_free_model(llama_model * model);
int llama_model_eval_count(const llama_model * model);
int llama_model_n_tokens(const llama_model * model);
int llama_model_n_ctx(const llama_model * model);
int llama_model_n_embd(const llama_model * model);
const char * llama_model_name(const llama_model * model);
int llama_model_n_vocab(const llama_model * model);
int llama_model_n_head(const llama_model * model);
int llama_model_n_head_kv(const llama_model * model);
int llama_model_n_layer(const llama_model * model);

// Context functions
llama_context * llama_init_from_model(llama_model * model, llama_context_params params);
void llama_free(llama_context * ctx);
llama_token llama_token_eos(const llama_model * model);
llama_token llama_token_bos(const llama_model * model);
llama_token llama_token_nl(const llama_model * model);

// Tokenization
int llama_tokenize(
        const llama_model * model,
        const char * text,
        llama_token * tokens,
        int n_max_tokens,
        bool add_special,
        bool parse_special
);

// Decoding
int llama_decode(llama_context * ctx, llama_token token);

// Sampling
llama_token llama_sample_token(llama_context * ctx);
llama_token llama_sample_token_greedy(llama_context * ctx);
void llama_sample_softmax(llama_context * ctx, llama_token * tokens, int n);
void llama_sample_top_k(llama_context * ctx, llama_token * tokens, int n, int k);
void llama_sample_top_p(llama_context * ctx, llama_token * tokens, int n, float p, float temp);
void llama_sample_temperature(llama_context * ctx, llama_token * tokens, int n, float temp);
void llama_sample_repetition_penalty(llama_context * ctx, llama_token * tokens, int n, float penalty);

// Token conversion
char * llama_token_to_piece(const llama_model * model, llama_token token);
char * llama_token_to_str(const llama_model * model, llama_token token);

// KV cache
int llama_kv_cache_used_cells(const llama_context * ctx);
bool llama_kv_cache_can_free(const llama_context * ctx);
void llama_kv_cache_clear(llama_context * ctx);
void llama_kv_cache_seq_rm(llama_context * ctx, int seq_id, int c0, int c1);
void llama_kv_cache_seq_cp(llama_context * ctx, int src_seq_id, int dst_seq_id, int c0, int c1);
void llama_kv_cache_seq_shift(llama_context * ctx, int seq_id, int c0, int c1, int n);
void llama_kv_cache_update(llama_context * ctx);

// Sequence operations
void llama_eval(llama_context * ctx, int n_tokens);
void llama_reset_timings(llama_context * ctx);
void llama_print_timings(llama_context * ctx);

// State
size_t llama_state_get_size(const llama_context * ctx);
size_t llama_state_save(llama_context * ctx, uint8_t * dst, size_t size);
size_t llama_state_load(llama_context * ctx, uint8_t * src, size_t size);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_H
