package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.MetpQcJudgePara;
import com.fc.v2.model.custom.MetpQcVerdict;
import com.fc.v2.service.ITMetpQcLineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 观测数据质量控制界限 Controller（直调门）。
 * 断与判的差事由服务层包圆，本层只传参、回话；
 * 认不认以回话为一家之言，页面不得先钉死结果；
 * 添、改、删三道闸一概封严，已出的结论不许手工补盖。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "观测数据质量控制界限")
@Controller
@RequestMapping("/MetpQcLineController")
public class MetpQcLineController extends BaseController {

    @Autowired
    private ITMetpQcLineService metpQcLineService;

    /**
     * 直调：判定一份观测的资料质量档位。
     * 结论压进所依据界限的代号与启用之日；同一时次何时判定一字不差。
     */
    @Log(title = "观测质控直调判定", action = "direct")
    @ApiOperation(value = "直调判定", notes = "按观测时次生效的界限判档，结论压进代号与启用之日")
    @PostMapping("/direct")
    @RequiresPermissions("metp:metpQcLine:direct")
    @ResponseBody
    public AjaxResult direct(MetpQcJudgePara para) {
        MetpQcVerdict verdict = metpQcLineService.judge(para);
        if (verdict.isAccepted()) {
            return AjaxResult.success(200, verdict.getMsg(), verdict);
        }
        return AjaxResult.error(verdict.getMsg()).put("data", verdict);
    }

    /** 某时次可用界限（顺位降序、代号降序），供核稿台与自查页面同口径核对 */
    @ApiOperation(value = "时次可用界限", notes = "某时次生效窗内的界限，顺位降序、代号降序")
    @GetMapping("/available")
    @RequiresPermissions("metp:metpQcLine:list")
    @ResponseBody
    public AjaxResult available(Date at) {
        return AjaxResult.success(200, "查询成功", metpQcLineService.listAvailable(at));
    }

    /** 主键查询 */
    @ApiOperation(value = "主键查询", notes = "按主键查界限")
    @GetMapping("/getInfo/{id}")
    @RequiresPermissions("metp:metpQcLine:view")
    @ResponseBody
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(200, "查询成功", metpQcLineService.selectTMetpQcLineById(id));
    }
}
