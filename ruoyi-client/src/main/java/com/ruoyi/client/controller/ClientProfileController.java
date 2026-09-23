package com.ruoyi.client.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 终端 - 当前登录人信息
 *
 * <p>终端前端在登录之后需要知道「我是谁」（顶栏显示囚号与姓名），而管理后台的
 * {@code /getInfo} 在 ruoyi-admin 里，终端进程不依赖那个模块、拿不到它，
 * 因此这里提供一个最小实现，只回传终端实际要用的两个字段。</p>
 *
 * <p>刻意不返回 roles / permissions：囚犯角色（ssk_prisoner）没有任何菜单与权限位，
 * 终端接口也一律不挂 {@code @PreAuthorize}，返回一份空权限列表只会让前端误以为
 * 需要按权限渲染菜单。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/client/profile")
public class ClientProfileController extends BaseController
{
    /**
     * 查询当前登录的囚犯信息
     *
     * @return 囚号与姓名
     */
    @GetMapping
    public AjaxResult profile()
    {
        // 囚号即登录名：不另建映射表，令牌里的 username 就是囚号
        String prisonerNumber = SecurityUtils.getUsername();
        SysUser user = SecurityUtils.getLoginUser().getUser();
        AjaxResult ajax = AjaxResult.success();
        ajax.put("prisonerNumber", prisonerNumber);
        ajax.put("nickName", user == null ? null : user.getNickName());
        return ajax;
    }
}
