package org.javatop.tienchin.framework.web.service;

import javax.annotation.Resource;

import org.javatop.tienchin.common.core.domain.entity.SysUser;
import org.javatop.tienchin.common.core.domain.model.LoginUser;
import org.javatop.tienchin.common.core.redis.RedisCache;
import org.javatop.tienchin.common.utils.DateUtils;
import org.javatop.tienchin.common.utils.MessageUtils;
import org.javatop.tienchin.common.utils.ServletUtils;
import org.javatop.tienchin.common.utils.StringUtils;
import org.javatop.tienchin.common.utils.ip.IpUtils;
import org.javatop.tienchin.framework.manager.AsyncManager;
import org.javatop.tienchin.framework.manager.factory.AsyncFactory;
import org.javatop.tienchin.system.service.ISysConfigService;
import org.javatop.tienchin.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.javatop.tienchin.common.constant.Constants;
import org.javatop.tienchin.common.exception.ServiceException;
import org.javatop.tienchin.common.exception.user.CaptchaException;
import org.javatop.tienchin.common.exception.user.CaptchaExpireException;
import org.javatop.tienchin.common.exception.user.UserPasswordNotMatchException;

/**
 * 登录校验方法
 *
 * @author tienchin
 */
@Component
public class SysLoginService {
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    /**
     * 登录验证
     *
     * @param username 用户名
     * @param password 密码
     * @param code     验证码
     * @param uuid     唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid) {
        boolean captchaOnOff = configService.selectCaptchaOnOff();
        // 验证码开关
        if (captchaOnOff) {
            validateCaptcha(username, code, uuid);
        }
        // 用户验证
        Authentication authentication = null;
        try {
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (Exception e) {
            if (e instanceof BadCredentialsException) {
                // Record login information
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                // Throw an exception
                throw new UserPasswordNotMatchException();
            } else {
                // Record login information
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                // Throw an exception
                throw new ServiceException(e.getMessage());
            }
        }
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        //获取登录用户信息
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        //记录登录信息
        recordLoginInfo(loginUser.getUserId());
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = Constants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        //从redis中获取验证码
        String captcha = redisCache.getCacheObject(verifyKey);
        //删除验证码
        redisCache.deleteObject(verifyKey);
        //如果验证码为空，则抛出验证码过期异常
        if (captcha == null) {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
            throw new CaptchaExpireException();
        }
        //如果输入的验证码不正确，则抛出验证码错误异常
        if (!code.equalsIgnoreCase(captcha)) {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId) {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        //获取登录IP地址
        sysUser.setLoginIp(IpUtils.getIpAddr(ServletUtils.getRequest()));
        //设置登录日期
        sysUser.setLoginDate(DateUtils.getNowDate());
        //更新用户信息
        userService.updateUserProfile(sysUser);
    }
}
