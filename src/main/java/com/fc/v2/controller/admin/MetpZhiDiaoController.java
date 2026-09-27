package com.fc.v2.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoQo;
import com.fc.v2.model.custom.zhidiao.MetpZhiDiaoVerdict;
import com.fc.v2.service.ITMetpZhiDiaoService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 「直调」门：观测资料质量档位直调判定。
 *
 * 门规：
 * - 只此一道只读口径，多路参数每请新载一副（Spring 现场绑定 {@link MetpZhiDiaoQo}，不留存）；
 * - 断与判的差事由服务层包圆，本门只如实回话；页面不得先把结果钉死，认不认服务层一家说了算；
 * - 添、改、删三道闸一概封严：本门无 add/edit/remove，也无任何页面；
 * - 已有签名（ITMetpQcLineService 六法）原样未动，直调另立门户。
 *
 * 回话：退回(REJECTED) 400；无界限可用(NO_LINE) 与判档成立(JUDGED) 均 200，
 * 省台核稿台与县局自查页面所见同一印文。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "观测资料质量档位直调")
@Controller
@RequestMapping("/MetpZhiDiaoController")
public class MetpZhiDiaoController extends BaseController {

    @Autowired
    private ITMetpZhiDiaoService metpZhiDiaoService;

    @ApiOperation(value = "观测直调判档", notes = "入参原文收，断与判皆由服务层；只读，无增改删")
    @GetMapping("/judge")
    @ResponseBody
    public AjaxResult judge(MetpZhiDiaoQo qo) {
        MetpZhiDiaoVerdict verdict = metpZhiDiaoService.judge(qo);
        AjaxResult body = AjaxResult.successData(200, verdict)
                .put("seal", verdict.sealText());
        if (verdict.getStatus() == MetpZhiDiaoVerdict.Status.REJECTED) {
            // 不成体统：原样退回，不出结论
            body.put("code", 400);
            body.put("msg", verdict.getMessage());
        }
        return body;
    }
}
