package com.fc.v2.service;

import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoQo;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoVerdict;

/**
 * 「直调」：观测资料质量档位直调判定。
 *
 * 断（输入成不成体统、该用哪条界限）与判（入哪一档、压不压线）全由服务层包圆；
 * 只读，不设添、改、删任何入口。
 *
 * @author fuce
 * @date 2026-09-27
 */
public interface ITMetpZhiDiaoService {

    /**
     * 对一份观测出质控结论。
     * 同一份观测重复调用，结论逐字一致；结论压有所依据界限的代号与启用之日，
     * 不含出结论的当下时刻，事后也无须、且无处手工补印。
     */
    MetpZhiDiaoVerdict judge(MetpZhiDiaoQo qo);
}
