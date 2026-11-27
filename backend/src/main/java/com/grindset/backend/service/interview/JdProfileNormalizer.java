package com.grindset.backend.service.interview;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.JdProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位画像的清洗与权重归一化。模型解析和前端预览回传都走这里,
 * 保证落库画像的结构、长度、权重一致。
 */
public final class JdProfileNormalizer {

    private JdProfileNormalizer() {
    }

    public static JdProfile normalize(JdProfile profile) {
        if (profile == null) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "模型输出为空");
        }
        List<JdProfile.Competency> competencies = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (profile.competencies() != null) {
            for (JdProfile.Competency item : profile.competencies()) {
                if (item == null || Texts.blankToNull(item.name()) == null) {
                    continue;
                }
                String name = Texts.truncate(item.name().strip(), 32);
                if (names.contains(name)) {
                    continue;
                }
                names.add(name);
                competencies.add(new JdProfile.Competency(
                        name,
                        item.weight(),
                        Texts.truncate(Texts.blankToNull(item.expectation()), 80)));
                if (competencies.size() >= 6) {
                    break;
                }
            }
        }
        if (competencies.size() < 3) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "JD 能力项不足");
        }
        return new JdProfile(
                Texts.truncate(Texts.blankToNull(profile.title()), 64),
                Texts.truncate(Texts.blankToNull(profile.company()), 64),
                Texts.truncate(Texts.blankToNull(profile.level()), 32),
                limitText(profile.mustHave(), 6, 40),
                limitText(profile.niceToHave(), 6, 40),
                limitText(profile.responsibilities(), 6, 40),
                normalizeWeights(competencies),
                limitText(profile.resumeGaps(), 4, 60));
    }

    /** 权重归一化到合计 100,避免模型给出 0 或总和不对 */
    private static List<JdProfile.Competency> normalizeWeights(List<JdProfile.Competency> competencies) {
        int sum = 0;
        for (JdProfile.Competency item : competencies) {
            sum += item.weight() == null ? 1 : Math.max(1, item.weight());
        }
        List<JdProfile.Competency> result = new ArrayList<>(competencies.size());
        int remaining = 100;
        for (int i = 0; i < competencies.size(); i++) {
            JdProfile.Competency item = competencies.get(i);
            int raw = item.weight() == null ? 1 : Math.max(1, item.weight());
            int weight = i == competencies.size() - 1
                    ? remaining
                    : (int) Math.round(raw * 100.0 / sum);
            int reserve = competencies.size() - 1 - i;
            weight = Math.min(weight, remaining - reserve);
            weight = Math.max(1, weight);
            remaining -= weight;
            result.add(new JdProfile.Competency(item.name(), weight, item.expectation()));
        }
        return result;
    }

    private static List<String> limitText(List<String> values, int max, int length) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String text = Texts.blankToNull(value);
            if (text != null && result.size() < max) {
                result.add(Texts.truncate(text.strip(), length));
            }
        }
        return result;
    }
}
