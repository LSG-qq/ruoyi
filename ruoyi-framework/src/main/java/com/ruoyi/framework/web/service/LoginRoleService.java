package com.ruoyi.framework.web.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 登录准入校验：按角色决定「哪些账号能登录本服务」
 *
 * <p>管理后台（ruoyi-admin）与终端服务（ruoyi-client）是两个独立进程、各有各的 token 密钥，
 * 但共用同一张 {@code sys_user}。如果没有这道门，囚犯用囚号+密码就能登录管理后台——
 * 登录本身不报错，而管理端存在一批「只要求登录、没挂 {@code @PreAuthorize}」的接口
 * （{@code /common/upload}、{@code /common/download}、{@code /test/user/*} 等），
 * 等于把文件上传下载与演示接口对囚犯开放。</p>
 *
 * <p>在登录入口按角色一次性拦住，比逐个给那些接口补权限更可靠：
 * 拿不到 token，后面所有接口对他都不可达；将来若依升级带来新的裸奔接口，这道门依然有效。</p>
 *
 * <p>两端靠配置各自声明准入范围（角色值就是 {@code sys_role.role_key}）：</p>
 * <ul>
 *     <li>终端服务 {@code login.allowed-roles: ssk_prisoner} —— 只有囚犯能登录终端；</li>
 *     <li>管理后台 {@code login.denied-roles: ssk_prisoner} —— 囚犯禁止登录管理后台。</li>
 * </ul>
 *
 * <p><b>调用时机</b>：由 {@link SysLoginService#login} 在「密码校验通过之后、写登录日志之前」调用，
 * 不要挪到 Controller 最前面。三个原因：</p>
 * <ol>
 *     <li>验证码与 IP 黑名单校验都在 {@code SysLoginService.login} 内部且先于密码校验执行。
 *         若把准入校验提到最前，攻击者不用解验证码、从被封 IP 也能拿到
 *         「该账号无权登录本系统」这种确定性回包，等于提供了一个「批量确认哪些用户名是囚犯」的枚举口子；</li>
 *     <li>放在密码校验之后，只有已经掌握正确口令的调用方才可能观察到角色差异，没有可利用的枚举面；</li>
 *     <li>此时 {@code LoginUser} 已在手，直接把 {@code SysUser} 传进来即可，不用再查一次库。</li>
 * </ol>
 *
 * <p>本类只对外提供「已知用户、判断放不放行」这一件事。刻意不提供按用户名自查的重载：
 * 那种方法要自己查库，且「用户不存在时静默放行」的语义只在登录入口成立，
 * 换个调用点就会变成一句看起来全面、实际会漏判的校验。</p>
 *
 * @author ruoyi
 */
@Component
public class LoginRoleService
{
    private static final Logger log = LoggerFactory.getLogger(LoginRoleService.class);

    /**
     * 允许登录本服务的角色，逗号分隔；留空表示不限制
     */
    @Value("${login.allowed-roles:}")
    private String allowedRoles;

    /**
     * 禁止登录本服务的角色，逗号分隔；优先级高于 {@link #allowedRoles}
     */
    @Value("${login.denied-roles:}")
    private String deniedRoles;

    /**
     * 角色解析复用权限服务，保证与 {@code @ss.hasRole} 的判断口径一致
     * （其中超级管理员会被解析成 {@code admin} 角色）
     */
    @Autowired
    private SysPermissionService permissionService;

    /**
     * 启动时把生效的准入策略打进日志
     *
     * <p>两个配置都没有默认值（{@code @Value} 的冒号后为空），也就是**漏配等于不限制登录**。
     * 这是刻意保持与若依原生一致的默认行为，但失败方向是「放开」，
     * 一旦某个进程漏配就会静默地把管理后台对囚犯开放，从运行日志上看不出任何异常。
     * 启动时打一行日志，让「本进程到底放不放囚犯进来」在启动日志里一眼可见：
     * 配了打 INFO，没配打 WARN。</p>
     */
    @PostConstruct
    public void reportPolicy()
    {
        Set<String> allowed = splitRoles(allowedRoles);
        Set<String> denied = splitRoles(deniedRoles);
        if (allowed.isEmpty() && denied.isEmpty())
        {
            log.warn("登录准入未配置（login.allowed-roles 与 login.denied-roles 均为空），本服务不限制账号来源，任何角色都可登录");
            return;
        }
        log.info("登录准入已启用：允许登录的角色={}，禁止登录的角色={}",
                allowed.isEmpty() ? "（不限）" : allowed,
                denied.isEmpty() ? "（无）" : denied);
    }

    /**
     * 校验指定用户是否允许登录本服务，不允许时抛出业务异常中断登录
     *
     * @param user 已查出的用户，为 {@code null} 时直接放行交给后续校验统一提示
     */
    public void checkAccess(SysUser user)
    {
        if (isUnrestricted() || user == null)
        {
            return;
        }

        Set<String> allowed = splitRoles(allowedRoles);
        Set<String> denied = splitRoles(deniedRoles);
        Set<String> roles = permissionService.getRolePermission(user);

        // 禁止优先：黑名单命中即拒，不看白名单
        if (!Collections.disjoint(roles, denied))
        {
            throw new ServiceException("当前账号无权登录本系统，请联系管理员");
        }
        // 白名单非空时要求至少命中一个角色；为空表示「只要不在黑名单就放行」
        if (!allowed.isEmpty() && Collections.disjoint(roles, allowed))
        {
            throw new ServiceException("当前账号无权登录本系统，请联系管理员");
        }
    }

    /**
     * 判断当前服务是否未配置任何准入限制
     *
     * @return true 表示白名单与黑名单均为空
     */
    private boolean isUnrestricted()
    {
        return StringUtils.isEmpty(allowedRoles) && StringUtils.isEmpty(deniedRoles);
    }

    /**
     * 把逗号分隔的角色配置解析为集合
     *
     * <p>用 {@link LinkedHashSet} 顺带去重，运维把同一个角色写两遍也不会影响判断。</p>
     *
     * @param roles 配置值
     * @return 角色集合，未配置或全为空白时返回空集合
     */
    private Set<String> splitRoles(String roles)
    {
        if (StringUtils.isEmpty(roles))
        {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(roles.split(","))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
