package com.neuralmind.data.repository

import android.util.Log
import com.neuralmind.data.local.db.dao.SkillDao
import com.neuralmind.data.local.db.entity.SkillEntity
import com.neuralmind.domain.model.Skill
import com.neuralmind.domain.model.SkillCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepository @Inject constructor(
    private val skillDao: SkillDao
) {
    companion object {
        private const val TAG = "SkillRepository"
    }

    fun getAllSkills(): Flow<List<Skill>> {
        return skillDao.getAllSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getSkillsByCategory(category: SkillCategory): Flow<List<Skill>> {
        return getAllSkills().map { skills ->
            skills.filter { it.category == category }
        }
    }

    fun getInstalledSkills(): Flow<List<Skill>> {
        return skillDao.getInstalledSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getActiveSkills(): Flow<List<Skill>> {
        return skillDao.getActiveSkills().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSkillById(id: String): Skill? {
        return try {
            skillDao.getSkillById(id)?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting skill by id: $id", e)
            null
        }
    }

    suspend fun installSkill(skillId: String) {
        try {
            skillDao.setInstalled(skillId, true)
            skillDao.setActive(skillId, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing skill: $skillId", e)
        }
    }

    suspend fun uninstallSkill(skillId: String) {
        try {
            skillDao.setActive(skillId, false)
            skillDao.setInstalled(skillId, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling skill: $skillId", e)
        }
    }

    suspend fun activateSkill(skillId: String) {
        try {
            val skill = skillDao.getSkillById(skillId)
            if (skill?.isInstalled != true) return
            skillDao.setActive(skillId, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error activating skill: $skillId", e)
        }
    }

    suspend fun deactivateSkill(skillId: String) {
        try {
            skillDao.setActive(skillId, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating skill: $skillId", e)
        }
    }

    suspend fun getActiveSystemPrompts(): String {
        return try {
            val activeSkills = skillDao.getActiveSkills().first()
            if (activeSkills.isEmpty()) return ""
            
            val prompts = activeSkills.filter { it.systemPrompt.isNotBlank() }
                .joinToString("\n\n") { "【${it.name}】\n${it.systemPrompt}" }
            
            if (prompts.isNotBlank()) {
                "\n\n你可以使用以下技能来帮助用户：\n$prompts"
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active system prompts", e)
            ""
        }
    }

    suspend fun insertDefaultSkills() {
        try {
            if (skillDao.getSkillById("calculator") != null) return

            val defaultSkills = listOf(
                // ========== 效率类 (PRODUCTIVITY) ==========
                SkillEntity(
                    id = "writing-assistant",
                    name = "写作助手",
                    description = "帮助润色文章、改写文案、优化表达",
                    detailedDescription = "专业的写作辅助工具，可以帮助你改善文章表达、润色措辞、调整语气，让文字更加流畅专业。",
                    icon = "edit_note",
                    category = SkillCategory.PRODUCTIVITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = true,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的中文写作助手，擅长帮助用户改善文字表达。请遵循以下原则：1. 保持原文的核心意思不变 2. 优化语言表达 3. 根据用户需求调整语气 4. 指出语法错误 5. 提供修改建议和理由 6. 默认使用简洁专业的风格 7. 可根据场景提供不同版本。请用中文回复。",
                    scenarios = "润色文章、修改简历、优化邮件措辞、朋友圈文案、公文写作",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "summarizer",
                    name = "内容总结",
                    description = "长文压缩、要点提取、关键信息归纳",
                    detailedDescription = "快速从长篇文章、会议记录、报告中提取核心要点，生成简洁准确的摘要。",
                    icon = "compress",
                    category = SkillCategory.PRODUCTIVITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的内容摘要助手，擅长从长文本中提取核心信息。请遵循以下原则：1. 准确理解核心主题 2. 简洁概括主要观点 3. 保留重要数据和结论 4. 结构化输出 5. 调整摘要长度 6. 区分事实和观点。请用中文回复。",
                    scenarios = "会议纪要、长文压缩、报告摘要、新闻速览、论文总结",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "translator-pro",
                    name = "翻译官",
                    description = "专业翻译、多语言互译、本地化表达",
                    detailedDescription = "支持中英日韩等多语言互译，提供自然流畅的本地化翻译结果。",
                    icon = "translate",
                    category = SkillCategory.PRODUCTIVITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的中文翻译官，精通多种语言的文化和表达习惯。请遵循以下原则：1. 准确理解原文含义 2. 译文符合目标语言习惯 3. 注意文化差异 4. 保持原文语气风格 5. 专业术语使用约定俗成翻译 6. 有歧义提供多种方案。请用中文回复。",
                    scenarios = "中英互译、文档翻译、邮件翻译、合同翻译、旅游对话",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "email-writer",
                    name = "邮件助手",
                    description = "商务邮件撰写、格式规范、语气调整",
                    detailedDescription = "快速撰写专业的商务邮件，自动调整语气和格式。",
                    icon = "email",
                    category = SkillCategory.PRODUCTIVITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的职场邮件撰写助手。请遵循以下原则：1. 规范邮件格式 2. 根据场景调整语气 3. 开头明确目的 4. 内容简洁有条理 5. 避免口语化表达 6. 可提供多个版本。请用中文回复。",
                    scenarios = "商务邮件、工作汇报、申请邮件、道歉邮件、节日祝福",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "meeting-notes",
                    name = "会议记录",
                    description = "会议纪要整理、待办提取、决议归纳",
                    detailedDescription = "将会议讨论内容整理成结构化的会议纪要，自动提取待办事项和决议。",
                    icon = "event_note",
                    category = SkillCategory.PRODUCTIVITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的会议记录助手。请遵循以下原则：1. 结构化整理 2. 标注待办责任人和截止时间 3. 准确记录决议 4. 使用专业简洁语言 5. 总结会议成果。请用中文回复。",
                    scenarios = "会议纪要、周会总结、项目会议、面试记录",
                    isActive = false,
                    isAvailable = true
                ),

                // ========== 创意类 (CREATIVE) ==========
                SkillEntity(
                    id = "storyteller",
                    name = "故事创作",
                    description = "短篇故事、小说续写、创意写作",
                    detailedDescription = "发挥创意，帮你创作引人入胜的故事。",
                    icon = "auto_stories",
                    category = SkillCategory.CREATIVE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位富有创意的故事创作助手。请遵循以下原则：1. 情节引人入胜 2. 人物形象鲜明 3. 环境描写生动 4. 对话自然真实 5. 主题明确有深度 6. 可根据需求调整风格。请用中文回复。",
                    scenarios = "短篇故事、小说续写、创意写作、剧本创作",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "poet",
                    name = "诗人",
                    description = "诗词创作、古风文案、文学鉴赏",
                    detailedDescription = "擅长古典诗词和现代诗歌创作，也提供文学鉴赏和分析。",
                    icon = "create",
                    category = SkillCategory.CREATIVE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位精通诗词文学的创作助手。请遵循以下原则：1. 遵循诗词格律 2. 意境优美深远 3. 用典恰当 4. 可创作现代诗 5. 提供鉴赏分析 6. 解释创作技巧。请用中文回复。",
                    scenarios = "古诗词创作、现代诗歌、文学鉴赏、文案创意",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "brainstorm",
                    name = "灵感激发",
                    description = "创意发散、头脑风暴、问题多角度分析",
                    detailedDescription = "帮你从不同角度思考问题，激发创意灵感。",
                    icon = "lightbulb",
                    category = SkillCategory.CREATIVE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位创意思维激发助手。请遵循以下原则：1. 从多角度思考 2. 提供大胆创新的想法 3. 结合类比和联想 4. 鼓励非常规思路 5. 帮助筛选和优化 6. 提供可执行的方案。请用中文回复。",
                    scenarios = "头脑风暴、创意发散、问题分析、项目策划",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "copywriter",
                    name = "文案策划",
                    description = "广告文案、营销内容、品牌slogan",
                    detailedDescription = "创作吸引眼球的营销文案。",
                    icon = "campaign",
                    category = SkillCategory.CREATIVE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位资深文案策划专家。请遵循以下原则：1. 理解品牌核心价值 2. 文案简洁有力 3. 突出差异化卖点 4. 了解目标受众 5. 可提供多版本 6. 注意渠道差异。请用中文回复。",
                    scenarios = "品牌slogan、产品文案、社交媒体、节日营销",
                    isActive = false,
                    isAvailable = true
                ),

                // ========== 学习类 (LEARNING) ==========
                SkillEntity(
                    id = "tutor",
                    name = "学习导师",
                    description = "知识讲解、概念解析、举一反三",
                    detailedDescription = "用通俗易懂的方式讲解知识。",
                    icon = "school",
                    category = SkillCategory.LEARNING.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位耐心且专业的学习导师。请遵循以下原则：1. 通俗解释复杂概念 2. 善用类比和实例 3. 循序渐进 4. 鼓励主动思考 5. 温和纠正错误 6. 适时提供练习 7. 总结学习要点。请用中文回复。",
                    scenarios = "概念讲解、知识答疑、学习规划、考前复习",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "flashcard",
                    name = "知识卡片",
                    description = "制作学习卡片、知识点提炼、复习辅助",
                    detailedDescription = "帮你将知识点整理成记忆卡片。",
                    icon = "style",
                    category = SkillCategory.LEARNING.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的学习卡片制作助手。请遵循以下原则：1. 提炼成简洁问答对 2. 问题明确答案精准 3. 添加记忆技巧 4. 从多角度制作卡片 5. 格式统一便于复习。请用中文回复。",
                    scenarios = "背单词、知识点复习、考试重点、医学知识",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "quiz-master",
                    name = "出题官",
                    description = "生成练习题、考点分析、答案解析",
                    detailedDescription = "根据学习内容生成各种类型的练习题。",
                    icon = "quiz",
                    category = SkillCategory.LEARNING.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位经验丰富的出题官。请遵循以下原则：1. 针对性设计题目 2. 题型多样 3. 难度适中 4. 完整答案解析 5. 指出常见错误 6. 标注考点和难度。请用中文回复。",
                    scenarios = "生成练习题、考前模拟、知识点测试",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "explain-like-five",
                    name = "简单解释",
                    description = "用通俗语言解释复杂概念",
                    detailedDescription = "用最简单的语言解释专业术语和复杂概念。",
                    icon = "question_answer",
                    category = SkillCategory.LEARNING.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位擅长将复杂问题简单化的专家。请遵循以下原则：1. 假设对方是小白 2. 用日常例子类比 3. 避免专业术语 4. 语言生动有趣 5. 鼓励追问 6. 最后简要提及进阶解释。请用中文回复。",
                    scenarios = "术语解释、技术原理、科学概念、政策解读",
                    isActive = false,
                    isAvailable = true
                ),

                // ========== 工具类 (UTILITY) ==========
                SkillEntity(
                    id = "calculator",
                    name = "计算器",
                    description = "数学计算、公式推导、单位换算",
                    detailedDescription = "提供基础的数学计算能力。",
                    icon = "calculate",
                    category = SkillCategory.UTILITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = true,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位精确的计算助手。请遵循以下原则：1. 准确进行数学运算 2. 分步展示计算过程 3. 可进行单位换算 4. 适当解释方法 5. 结果保留合适精度 6. 发现错误温和纠正。请用中文回复。",
                    scenarios = "数学计算、单位换算、汇率转换、费用估算",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "code-helper",
                    name = "编程助手",
                    description = "代码片段、语法参考、算法解释",
                    detailedDescription = "帮助编写和理解代码。",
                    icon = "code",
                    category = SkillCategory.UTILITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位经验丰富的程序员。请遵循以下原则：1. 提供清晰代码示例 2. 解释逻辑和思路 3. 指出优缺点 4. 添加注释 5. 提醒常见错误 6. 可提供多种实现 7. 帮助调试优化。请用中文回复，代码用代码块。",
                    scenarios = "代码编写、语法查询、算法解释、代码调试",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "regex-builder",
                    name = "正则生成",
                    description = "自然语言生成正则表达式",
                    detailedDescription = "将需求描述转换为正则表达式。",
                    icon = "data_object",
                    category = SkillCategory.UTILITY.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位正则表达式专家。请遵循以下原则：1. 准确理解需求 2. 提供简洁高效的正则 3. 详细解释每部分含义 4. 提供匹配示例 5. 指出常见陷阱 6. 可提供不同语言版本。请用中文回复。",
                    scenarios = "表单验证、文本提取、数据清洗、模式匹配",
                    isActive = false,
                    isAvailable = true
                ),

                // ========== 生活类 (LIFESTYLE) ==========
                SkillEntity(
                    id = "recipe-advisor",
                    name = "美食顾问",
                    description = "菜谱推荐、食材搭配、烹饪技巧",
                    detailedDescription = "推荐美味菜谱，解答烹饪问题。",
                    icon = "restaurant",
                    category = SkillCategory.LIFESTYLE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位热爱美食的烹饪专家。请遵循以下原则：1. 根据食材推荐菜谱 2. 详细步骤说明 3. 标注关键技巧 4. 提供替代食材 5. 解答烹饪问题 6. 推荐时令菜谱 7. 提供营养参考。请用中文回复。",
                    scenarios = "菜谱推荐、烹饪指导、食材搭配、健康饮食",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "travel-planner",
                    name = "旅行规划",
                    description = "行程安排、景点推荐、攻略制定",
                    detailedDescription = "帮你规划完美的旅行行程。",
                    icon = "flight",
                    category = SkillCategory.LIFESTYLE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位经验丰富的旅行规划师。请遵循以下原则：1. 根据需求制定行程 2. 推荐特色景点 3. 提供交通住宿建议 4. 标注预约信息 5. 避免行程过紧 6. 推荐当地美食 7. 提供实用提示。请用中文回复。",
                    scenarios = "行程规划、景点推荐、美食攻略、住宿建议",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "fitness-coach",
                    name = "健身教练",
                    description = "运动计划、动作指导、饮食建议",
                    detailedDescription = "制定个性化健身计划。",
                    icon = "fitness_center",
                    category = SkillCategory.LIFESTYLE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位专业的健身教练。请遵循以下原则：1. 根据状况制定计划 2. 清晰动作示范 3. 标注常见错误 4. 合理安排强度 5. 提供饮食建议 6. 适时调整计划 7. 安全第一。请用中文回复。",
                    scenarios = "健身计划、动作指导、减脂增肌、饮食建议",
                    isActive = false,
                    isAvailable = true
                ),
                SkillEntity(
                    id = "daily-planner",
                    name = "日程管理",
                    description = "时间规划、任务优先级、效率提升",
                    detailedDescription = "帮你规划每日任务，提高效率。",
                    icon = "schedule",
                    category = SkillCategory.LIFESTYLE.name,
                    version = "1.0",
                    author = "NeuralMind",
                    permissions = "[]",
                    isInstalled = false,
                    isBuiltIn = true,
                    downloadUrl = null,
                    installedSize = 0L,
                    systemPrompt = "你是一位高效的时间管理专家。请遵循以下原则：1. 帮助梳理和分解任务 2. 按重要紧急排优先级 3. 合理分配时间 4. 建议高效工作方式 5. 提醒适当休息 6. 帮助养成良好习惯。请用中文回复。",
                    scenarios = "每日计划、任务清单、时间管理、效率提升",
                    isActive = false,
                    isAvailable = true
                )
            )

            defaultSkills.forEach { skillDao.insert(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting default skills", e)
        }
    }

    private fun SkillEntity.toDomain(): Skill {
        val permissionsList = try {
            permissions.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding(""") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }

        return Skill(
            id = id,
            name = name,
            description = description,
            detailedDescription = detailedDescription,
            icon = icon,
            category = try { SkillCategory.valueOf(category) } catch (e: Exception) { SkillCategory.UTILITY },
            version = version,
            author = author,
            permissions = permissionsList,
            isInstalled = isInstalled,
            isBuiltIn = isBuiltIn,
            downloadUrl = downloadUrl,
            installedSize = installedSize,
            systemPrompt = systemPrompt,
            scenarios = scenarios,
            isActive = isActive,
            isAvailable = isAvailable
        )
    }
}
