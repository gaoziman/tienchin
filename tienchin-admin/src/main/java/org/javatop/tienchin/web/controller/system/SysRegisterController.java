package org.javatop.tienchin.web.controller.system;

import org.javatop.tienchin.common.core.controller.BaseController;
import org.javatop.tienchin.common.core.domain.AjaxResult;
import org.javatop.tienchin.common.core.domain.model.RegisterBody;
import org.javatop.tienchin.common.utils.StringUtils;
import org.javatop.tienchin.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.javatop.tienchin.framework.web.service.SysRegisterService;

/**
 * 注册验证
 *
 * @author tienchin
 */
@RestController
public class SysRegisterController extends BaseController {
    @Autowired
    private SysRegisterService registerService;

    @Autowired
    private ISysConfigService configService;

    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody user) {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser")))) {
            return error("当前系统没有开启注册功能！");
        }
        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }
}
