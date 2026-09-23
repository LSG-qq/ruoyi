package com.ruoyi.framework.web.controller;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.google.code.kaptcha.Producer;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.sign.Base64;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 通用鉴权端点：登录与图形验证码。
 * <p>
 * 这两个端点与具体的前端入口无关，管理后台（ruoyi-admin）和终端服务（ruoyi-client）都需要，
 * 因此下沉到 framework 层只保留一份实现，两个入口各自启动时都能扫到，
 * 不需要任何一个入口模块去依赖另一个入口模块。
 * <p>
 * 注意：SecurityConfig 里把 /login、/register、/captchaImage 声明为匿名可访问，
 * 本类提供的正是其中的 /login 与 /captchaImage；/register 仍留在 ruoyi-admin（终端不需要注册）。
 *
 * @author ruoyi
 */
@RestController
public class AuthController
{
    /**
     * 登录校验服务，负责密码校验、验证码校验与登录日志记录
     */
    @Autowired
    private SysLoginService loginService;

    /**
     * 字符型验证码生成器（验证码类型为 char 时使用）
     */
    @Resource(name = "captchaProducer")
    private Producer captchaProducer;

    /**
     * 算术型验证码生成器（验证码类型为 math 时使用）
     */
    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    /**
     * redis 缓存，用于存放验证码答案
     */
    @Autowired
    private RedisCache redisCache;

    /**
     * 参数配置服务，用于读取「是否开启验证码」开关
     */
    @Autowired
    private ISysConfigService configService;

    /**
     * 登录方法
     * <p>
     * 校验通过后签发 token 返回给调用方，登录成功/失败都会写一条登录日志（由 SysLoginService 完成）。
     * <p>
     * 角色准入校验不在这里，而是在 {@link SysLoginService#login} 内部、
     * 密码校验通过之后、写登录日志之前完成，原因见 {@code LoginRoleService}。
     *
     * @param loginBody 登录信息（用户名、密码、验证码、验证码标识）
     * @return 成功时返回 token
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /**
     * 生成图形验证码
     * <p>
     * 开关关闭时只回传 captchaEnabled=false，不生成图片；
     * 开启时把答案按 uuid 存入 redis，图片以 base64 回传，前端凭 uuid 在登录时回传答案。
     *
     * @param response http 响应
     * @return 验证码开关、uuid 与 base64 图片
     * @throws IOException 图片写出失败
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCode(HttpServletResponse response) throws IOException
    {
        AjaxResult ajax = AjaxResult.success();
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        ajax.put("captchaEnabled", captchaEnabled);
        if (!captchaEnabled)
        {
            return ajax;
        }

        // 保存验证码信息
        String uuid = IdUtils.simpleUUID();
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;

        String capStr = null, code = null;
        BufferedImage image = null;

        // 生成验证码
        String captchaType = RuoYiConfig.getCaptchaType();
        if ("math".equals(captchaType))
        {
            String capText = captchaProducerMath.createText();
            capStr = capText.substring(0, capText.lastIndexOf("@"));
            code = capText.substring(capText.lastIndexOf("@") + 1);
            image = captchaProducerMath.createImage(capStr);
        }
        else if ("char".equals(captchaType))
        {
            capStr = code = captchaProducer.createText();
            image = captchaProducer.createImage(capStr);
        }

        redisCache.setCacheObject(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        // 转换流信息写出
        FastByteArrayOutputStream os = new FastByteArrayOutputStream();
        try
        {
            ImageIO.write(image, "jpg", os);
        }
        catch (IOException e)
        {
            return AjaxResult.error(e.getMessage());
        }

        ajax.put("uuid", uuid);
        ajax.put("img", Base64.encode(os.toByteArray()));
        return ajax;
    }
}
