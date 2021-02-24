package wms.controller;

import wms.cmmn.FilterUtil;
import wms.cmmn.UtilData;
import wms.cmmn.CommonProperties.SESSION_ID;
import wms.dto.CommonResult;
import wms.dto.LoginParam;
import wms.dto.UserDTO;
import wms.dto.UserMstListResult;
import wms.service.LoginService;
import wms.service.UserMstService;

import java.nio.file.attribute.UserDefinedFileAttributeView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/login")
public class LoginController extends CommonController {
	@Resource(name="loginService")
	public LoginService loginService;
	@Resource(name="userMstService")
	public UserMstService userMstService;
	
	/**
	 * 로그인 처리
	 * 
	 * @param loginParam
	 * @param model
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/LoginProc.do", method = RequestMethod.POST)
	public String getLoginPage(@RequestBody LoginParam loginParam, ModelMap model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		Logger usrLogger = UtilData.getLoggerInstance(request.getSession());		
		usrLogger.info("로그인 처리 : " + loginParam.getUserId());
		
		// 화면에서 받은 값을 필터링 후, 다시 세팅해서 DB에 넘김
		LoginParam resetParam = new LoginParam();
		resetParam.setUserId(FilterUtil.getXSSFilter(loginParam.getUserId()));
		resetParam.setUserId(FilterUtil.getSQLInjectionFilter(loginParam.getUserId()));
		
		resetParam.setPasswd(FilterUtil.getXSSFilter(loginParam.getPasswd()));
		resetParam.setPasswd(FilterUtil.getSQLInjectionFilter(loginParam.getPasswd()));

		UserMstListResult result = loginService.selectUserInfo(resetParam, request);
		
		model.addAttribute(JSON_STR, result);
		
		return "jsonView";
	}
	
}
