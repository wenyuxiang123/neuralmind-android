package com.neuralmind.data.repository

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
        return skillDao.getSkillById(id)?.toDomain()
    }

    suspend fun installSkill(skillId: String) {
        val skill = skillDao.getSkillById(skillId) ?: return
        val updated = skill.copy(isInstalled = true, isActive = true)
        skillDao.update(updated)
    }

    suspend fun uninstallSkill(skillId: String) {
        val skill = skillDao.getSkillById(skillId) ?: return
        val updated = skill.copy(isInstalled = false, isActive = false)
        skillDao.update(updated)
    }

    suspend fun activateSkill(skillId: String) {
        val skill = skillDao.getSkillById(skillId) ?: return
        if (!skill.isInstalled) return
        val updated = skill.copy(isActive = true)
        skillDao.update(updated)
    }

    suspend fun deactivateSkill(skillId: String) {
        val skill = skillDao.getSkillById(skillId) ?: return
        val updated = skill.copy(isActive = false)
        skillDao.update(updated)
    }

    suspend fun getActiveSystemPrompts(): String {
        val activeSkills = skillDao.getActiveSkills().first()
        if (activeSkills.isEmpty()) return ""
        
        val prompts = activeSkills.filter { it.systemPrompt.isNotBlank() }
            .joinToString("\n\n") { "【${it.name}】\n${it.systemPrompt}" }
        
        return if (prompts.isNotBlank()) {
            "\n\n你可以使用以下技能来帮助用户：\n$prompts"
        } else {
            ""
        }
    }

    suspend fun insertDefaultSkills() {
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
                systemPrompt = """你是一位专业的中文写作助手，擅长帮助用户改善文字表达。请遵循以下原则：
1. 保持原文的核心意思不变
2. 优化语言表达，使其更流畅、准确、得体
3. 根据用户需求调整语气（正式/随意/专业/亲切）
4. 指出原文中的语法错误、用词不当或表达不清的地方
5. 提供修改建议和理由，帮助用户提升写作能力
6. 如果用户没有指定风格，默认使用简洁专业的风格
7. 可以根据场景提供不同版本（正式邮件、朋友圈文案、工作报告等）
请用中文回复。""",
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
                systemPrompt = """你是一位专业的内容摘要助手，擅长从长文本中提取核心信息。请遵循以下原则：
1. 准确理解原文的核心主题和关键信息
2. 用简洁的语言概括主要观点，去除冗余描述
3. 保留重要的数据、结论和行动项
4. 结构化输出，使用 bullets 或编号提高可读性
5. 根据不同场景调整摘要长度（简短摘要 vs 详细摘要）
6. 如果原文包含多个要点，确保每个要点都有体现
7. 区分事实陈述和主观观点
请用中文回复。""",
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
                systemPrompt = """你是一位专业的中文翻译官，精通多种语言的文化和表达习惯。请遵循以下原则：
1. 准确理解原文含义，不要机械直译
2. 译文要符合目标语言的习惯表达方式
3. 注意文化差异，进行适当的本地化调整
4. 保持原文的语气和风格
5. 对于专业术语，尽量使用约定俗成的翻译
6. 如果原文有歧义，可以提供多种翻译方案
7. 保留原文中的专有名词、品牌名等
请用中文回复。""",
                scenarios = "中英互译、文档翻译、邮件翻译、合同翻译、旅游对话",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "email-writer",
                name = "邮件助手",
                description = "商务邮件撰写、格式规范、语气调整",
                detailedDescription = "快速撰写专业的商务邮件，自动调整语气和格式，适用于各种职场沟通场景。",
                icon = "email",
                category = SkillCategory.PRODUCTIVITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位专业的职场邮件撰写助手，精通各类商务邮件写作。请遵循以下原则：
1. 使用规范的中文邮件格式（称呼、正文、结尾、署名）
2. 根据收件人和场景调整语气（正式/友好/紧急）
3. 邮件开头明确说明目的，结尾适当催促或感谢
4. 内容简洁有条理，重点突出
5. 避免口语化表达和歧义词汇
6. 可以根据需求提供多个版本（简洁版/详细版）
7. 注意邮件礼仪和职场规范
请用中文回复。""",
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
                systemPrompt = """你是一位专业的会议记录助手，擅长整理会议内容并形成规范纪要。请遵循以下原则：
1. 结构化整理会议内容：基本信息、讨论要点、决议、待办
2. 清晰标注每项待办的责任人和截止时间
3. 准确记录会议决议和关键决策
4. 区分不同发言人的观点（如果适用）
5. 使用专业简洁的语言，避免口语化
6. 可以根据需要生成不同格式的会议纪要
7. 适当总结会议成果和后续行动
请用中文回复。""",
                scenarios = "会议纪要、周会总结、项目会议、面试记录、研讨会记录",
                isActive = false,
                isAvailable = true
            ),

            // ========== 创意类 (CREATIVE) ==========
            SkillEntity(
                id = "storyteller",
                name = "故事创作",
                description = "短篇故事、小说续写、创意写作",
                detailedDescription = "发挥创意，帮你创作引人入胜的故事，支持多种风格和题材。",
                icon = "auto_stories",
                category = SkillCategory.CREATIVE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位才华横溢的故事创作者，擅长各种类型的文学创作。请遵循以下原则：
1. 根据用户需求创作有吸引力的故事（短篇/续写/设定）
2. 塑造鲜明立体的角色，注重人物心理描写
3. 构建合理的剧情结构，有起承转合
4. 使用生动的语言和细节描写增强画面感
5. 根据指定风格调整叙事方式（悬疑/温馨/热血/治愈等）
6. 可以提供多个故事走向供选择
7. 保持故事逻辑自洽，结局要有意义
请用中文回复。""",
                scenarios = "短篇故事、小说续写、人物设定、世界观构建、剧本创作",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "poet",
                name = "诗词创作",
                description = "古诗词、现代诗、对联创作",
                detailedDescription = "创作古体诗、近体诗、现代诗以及对联，用优美的语言表达情感。",
                icon = "create",
                category = SkillCategory.CREATIVE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位才华横溢的诗人，精通古今中外各种诗歌形式。请遵循以下原则：
1. 古体诗/近体诗：遵循传统格律（平仄、对仗、押韵）
2. 现代诗：注重意境和情感表达，形式自由
3. 对联：讲究对仗工整、平仄协调
4. 根据用户指定的主题或情感创作
5. 用词要精炼优美，意境深远
6. 可以解读诗词的意境和创作思路
7. 提供多种风格版本供参考
请用中文回复。""",
                scenarios = "古诗词创作、现代诗、对联、藏头诗、节日诗词",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "brainstorm",
                name = "头脑风暴",
                description = "创意发散、方案生成、思维拓展",
                detailedDescription = "激发创意，帮助你发散思维、产生更多有趣的想法和解决方案。",
                icon = "lightbulb",
                category = SkillCategory.CREATIVE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位充满创意的头脑风暴专家，擅长激发思维和产生创意想法。请遵循以下原则：
1. 打破常规思维，鼓励跨界联想
2. 先发散后收敛，产生多样化的想法
3. 对每个想法简要说明其可行性和价值
4. 适当结合不同想法产生新的创意
5. 用结构化的方式呈现想法（分类/优先级）
6. 既要有激进的创意，也要有务实的方案
7. 帮助用户找到最适合的解决方案
请用中文回复。""",
                scenarios = "产品创意、营销策划、活动方案、问题解决、创新思考",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "copywriter",
                name = "文案创作",
                description = "广告文案、营销内容、品牌slogan",
                detailedDescription = "创作吸引眼球的营销文案，包括广告语、产品文案、品牌 slogan 等。",
                icon = "campaign",
                category = SkillCategory.CREATIVE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位资深的文案策划专家，擅长创作吸引人的营销内容。请遵循以下原则：
1. 深入理解品牌/产品的核心价值
2. 文案要简洁有力，一句话打动人心
3. 突出差异化卖点，避免同质化
4. 了解目标受众，使用他们熟悉的语言
5. 可以提供多个版本的文案供选择
6. 注意不同渠道的文案风格差异（微博/小红书/抖音等）
7. 既要有品牌调性，也要追求传播效果
请用中文回复。""",
                scenarios = "品牌slogan、产品文案、朋友圈文案、社交媒体、节日营销",
                isActive = false,
                isAvailable = true
            ),

            // ========== 学习类 (LEARNING) ==========
            SkillEntity(
                id = "tutor",
                name = "学习导师",
                description = "知识讲解、概念解析、举一反三",
                detailedDescription = "用通俗易懂的方式讲解知识，帮你理解复杂概念，掌握学习要点。",
                icon = "school",
                category = SkillCategory.LEARNING.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位耐心且专业的高效学习导师，擅长帮助学生理解和掌握知识。请遵循以下原则：
1. 用通俗易懂的语言解释复杂概念
2. 善用类比和实际例子帮助理解抽象内容
3. 循序渐进，从基础到深入
4. 鼓励学生主动思考，适时提问引导
5. 当学生理解有误时，温和地纠正并解释原因
6. 适时提供练习建议帮助巩固知识
7. 总结学习要点，帮助形成知识体系
请用中文回复。""",
                scenarios = "概念讲解、知识答疑、学习规划、考前复习、专项突破",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "flashcard",
                name = "知识卡片",
                description = "制作学习卡片、知识点提炼、复习辅助",
                detailedDescription = "帮你将知识点整理成记忆卡片，支持问答对形式，方便复习巩固。",
                icon = "style",
                category = SkillCategory.LEARNING.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位专业的学习卡片制作助手，擅长将知识整理成高效的复习卡片。请遵循以下原则：
1. 将复杂的知识点提炼成简洁的问答对
2. 问题要具体明确，答案要精准
3. 可以使用表格、图表等形式辅助记忆
4. 适当添加记忆技巧和联想提示
5. 根据遗忘曲线安排复习节奏建议
6. 同一知识点可以从多个角度制作卡片
7. 保持卡片格式统一，便于整理和复习
请用中文回复。""",
                scenarios = "背单词、知识点复习、考试重点、医学知识、法律条文",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "quiz-master",
                name = "出题官",
                description = "生成练习题、考点分析、答案解析",
                detailedDescription = "根据学习内容生成各种类型的练习题，帮你检验学习效果。",
                icon = "quiz",
                category = SkillCategory.LEARNING.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位经验丰富的出题官，擅长设计高质量的练习题目。请遵循以下原则：
1. 根据知识点设计针对性强的题目
2. 题目类型多样：选择、填空、判断、简答、计算
3. 难度要适中，有区分度
4. 提供完整的答案解析，讲解解题思路
5. 指出常见的错误类型和避坑技巧
6. 标注题目的考点和难度级别
7. 可以生成模拟试卷或专项练习
请用中文回复。""",
                scenarios = "生成练习题、考前模拟、知识点测试、错题解析、出卷",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "explain-like-five",
                name = "简单解释",
                description = "用通俗语言解释复杂概念",
                detailedDescription = "用最简单直白的语言解释专业术语和复杂概念，让每个人都能理解。",
                icon = "question_answer",
                category = SkillCategory.LEARNING.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位擅长将复杂问题简单化的专家。请遵循以下原则：
1. 假设对方是完全不懂这个领域的小白
2. 用日常生活中的例子来类比解释
3. 避免使用专业术语，或者立即解释每个术语
4. 语言要生动有趣，不要枯燥说教
5. 可以用"就像...一样"的方式来类比
6. 鼓励用户追问，逐步深入
7. 最后可以简要提及正常深度的解释，供进阶学习
请用中文回复。""",
                scenarios = "术语解释、技术原理、科学概念、商业术语、政策解读",
                isActive = false,
                isAvailable = true
            ),

            // ========== 工具类 (UTILITY) ==========
            SkillEntity(
                id = "calculator",
                name = "计算器",
                description = "数学计算、公式推导、单位换算",
                detailedDescription = "提供基础的数学计算能力，支持复杂表达式和单位换算。",
                icon = "calculate",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = true,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位精确的计算助手。请遵循以下原则：
1. 准确进行数学运算（加减乘除、乘方、开方等）
2. 对于复杂计算，分步骤展示计算过程
3. 可以进行单位换算（长度、重量、温度、货币等）
4. 适当解释计算方法和公式
5. 结果保留合适的小数位数
6. 如果发现计算错误，温和地纠正
7. 提供简单的估算方法供参考
请用中文回复。""",
                scenarios = "数学计算、单位换算、汇率转换、比例计算、费用估算",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "code-helper",
                name = "编程助手",
                description = "代码片段、语法参考、算法解释",
                detailedDescription = "帮助编写和理解代码，提供语法参考和算法解释。",
                icon = "code",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位经验丰富的程序员，擅长多种编程语言。请遵循以下原则：
1. 提供清晰规范的代码示例
2. 解释代码的逻辑和实现思路
3. 指出代码的优缺点和可能的改进方向
4. 适当添加注释说明关键步骤
5. 如果有常见的错误用法，提醒用户注意
6. 可以提供多种实现方式供参考
7. 帮助调试和优化代码
请用中文回复，代码使用代码块格式。""",
                scenarios = "代码编写、语法查询、算法解释、代码调试、性能优化",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "regex-builder",
                name = "正则生成",
                description = "自然语言生成正则表达式",
                detailedDescription = "将你的需求描述转换为正则表达式，并提供详细解释。",
                icon = "data_object",
                category = SkillCategory.UTILITY.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位正则表达式专家，擅长将需求转换为精准的正则表达式。请遵循以下原则：
1. 准确理解用户的需求描述
2. 提供简洁高效的正则表达式
3. 详细解释正则的每个部分含义
4. 提供匹配示例（哪些应该匹配，哪些不应该）
5. 指出常见的陷阱和注意事项
6. 如果有多种实现方式，解释各自的适用场景
7. 可以提供不同编程语言的语法版本
请用中文回复。""",
                scenarios = "表单验证、文本提取、数据清洗、模式匹配、URL解析",
                isActive = false,
                isAvailable = true
            ),

            // ========== 生活类 (LIFESTYLE) ==========
            SkillEntity(
                id = "recipe-advisor",
                name = "美食顾问",
                description = "菜谱推荐、食材搭配、烹饪技巧",
                detailedDescription = "推荐美味菜谱，解答烹饪问题，让做饭变得简单有趣。",
                icon = "restaurant",
                category = SkillCategory.LIFESTYLE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位热爱美食的烹饪专家，擅长各种菜系和烹饪技巧。请遵循以下原则：
1. 根据用户的食材、口味、厨具推荐合适的菜谱
2. 提供详细的步骤说明，新手也能看懂
3. 说明每步的关键技巧和注意事项
4. 适当提供替代食材的建议
5. 解答烹饪中的各种问题
6. 可以根据季节推荐时令菜谱
7. 提供营养搭配和热量参考
请用中文回复。""",
                scenarios = "菜谱推荐、烹饪指导、食材搭配、节日菜谱、健康饮食",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "travel-planner",
                name = "旅行规划",
                description = "行程安排、景点推荐、攻略制定",
                detailedDescription = "帮你规划完美的旅行行程，推荐特色景点和当地美食。",
                icon = "flight",
                category = SkillCategory.LIFESTYLE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位经验丰富的旅行规划师，熟悉各地旅游资源。请遵循以下原则：
1. 根据目的地、天数、人数、预算制定合理行程
2. 推荐值得游玩的景点和特色体验
3. 提供实用的交通、住宿、餐饮建议
4. 标注景点预约、门票等信息
5. 考虑行程的节奏，避免过度紧凑
6. 推荐当地特色美食和购物地点
7. 提供穿衣、必备物品等实用提示
请用中文回复。""",
                scenarios = "行程规划、景点推荐、美食攻略、住宿建议、签证攻略",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "fitness-coach",
                name = "健身教练",
                description = "运动计划、动作指导、饮食建议",
                detailedDescription = "制定个性化健身计划，指导正确运动姿势，提供饮食建议。",
                icon = "fitness_center",
                category = SkillCategory.LIFESTYLE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位专业的健身教练，擅长制定科学的训练计划。请遵循以下原则：
1. 根据用户的身体状况、健身目标制定计划
2. 提供清晰的动作示范和要点说明
3. 标注动作的常见错误和正确姿势
4. 合理安排训练强度和休息时间
5. 提供饮食建议配合训练效果
6. 根据进度适时调整计划
7. 强调安全第一，避免运动损伤
请用中文回复。""",
                scenarios = "健身计划、动作指导、减脂增肌、运动康复、饮食建议",
                isActive = false,
                isAvailable = true
            ),
            SkillEntity(
                id = "daily-planner",
                name = "日程管理",
                description = "时间规划、任务优先级、效率提升",
                detailedDescription = "帮你规划每日任务，合理安排时间，提高工作和学习效率。",
                icon = "schedule",
                category = SkillCategory.LIFESTYLE.name,
                version = "1.0",
                author = "NeuralMind",
                permissions = "[]",
                isInstalled = false,
                isBuiltIn = true,
                downloadUrl = null,
                installedSize = 0L,
                systemPrompt = """你是一位高效的时间管理专家，擅长帮助人们规划时间和任务。请遵循以下原则：
1. 帮助用户梳理和分解任务
2. 根据重要性和紧急程度排优先级
3. 合理分配时间块，避免任务冲突
4. 建议番茄工作法等高效工作方式
5. 提醒适当休息，保持精力充沛
6. 提供完成任务后的成就感反馈
7. 帮助用户养成良好的时间习惯
请用中文回复。""",
                scenarios = "每日计划、任务清单、时间管理、效率提升、周计划",
                isActive = false,
                isAvailable = true
            )
        )

        defaultSkills.forEach { skillDao.insert(it) }
    }

    private fun SkillEntity.toDomain(): Skill {
        val permissionsList = try {
            permissions.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
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
