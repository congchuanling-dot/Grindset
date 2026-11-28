package com.grindset.backend.service.training;

import java.util.List;

/**
 * 训练题来源扩展点。题库、AI、代码题各自实现，生成器只负责编排。
 */
public interface TrainingItemProvider {

    List<TrainingItemDraft> provide(TrainingGenerationContext context,
                                    List<TrainingTargetSlot> slots);
}
