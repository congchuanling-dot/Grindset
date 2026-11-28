package com.grindset.backend.service.training;

import java.util.List;

/**
 * 弱项查询端口。面试出题只依赖这个接口，不直接依赖掌握度实现，
 * 后续换存储或接入其它画像来源时不需要改面试模块。
 */
public interface WeakPointProvider {

    /** 返回当前用户最需要补的知识点名称，按优先级降序 */
    List<String> topWeakNames(long userId, int limit);
}
