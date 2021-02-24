package wms.controller;

import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import wms.cmmn.ConfigProperties;
import wms.cmmn.PickingSyncObj;
import wms.cmmn.UtilData;
import wms.dto.AreaMstParam;
import wms.dto.CommonResult;
import wms.dto.UserDTO;
import wms.dto.UserMenuDTO;
import wms.dto.UserMenuListResult;
import wms.dto.UserMstListResult;
import wms.dto.UserMstParam;
import wms.dto.UserRoleDTO;
import wms.dto.UserRoleManageListResult;
import wms.service.SystemService;
import wms.service.UserMstService;
import wms.service.UserRoleManageService;

@Controller
@RequestMapping("/sys")
public class UserMstController extends CommonController {
	@Resource(name="userMstService")	
	public UserMstService userMstService;
	
	/**
	 * 사용자 마스터 검색
	 * 
	 * @param param
	 * @param model
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/UserMstSearch.do", method = RequestMethod.POST)
	public String getSearchUserMstList(@RequestBody UserMstParam param, ModelMap model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		UserMstListResult result = userMstService.selectUserMstList(param, request);
		
		model.addAttribute(JSON_STR, result);
		
		return JSON_VIEW;
	}
	
	/**
	 *  로그인된 유저의 사용자정보 검색
	 * 
	 * @param param
	 * @param model
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/UserMstCurrentSearch.do", method = RequestMethod.POST)
	public String getCurrentUserMstList(@RequestBody UserMstParam param, ModelMap model, HttpServletRequest request, HttpServletResponse response) throws Exception {
	
		UserDTO userInfo = getUserInfo(request);
		param.setUserId(userInfo.getUserId());
		param.setExpiryKbn("");
		param.setUserName("");
		param.setWorkPlace("");
		
		UserMstListResult result = userMstService.selectUserMst(param, request);
		
		model.addAttribute(JSON_STR, result);
		
		return JSON_VIEW;
	}
	
	/**
	 * 사용자 마스터 삭제
	 * 
	 * @param paramList
	 * @param request
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value="/DeleteUserMst.do" , method = RequestMethod.POST)
	public String deleteAreaMst(@RequestBody List<UserMstParam> paramList, HttpServletRequest request, ModelMap model) throws Exception {
		CommonResult result = userMstService.deleteUserMst(paramList, request);
		
		model.addAttribute(JSON_STR, result);
		
		return JSON_VIEW;
	}
	
	/**
	 * 사용자 마스터 저장
	 * 
	 * @param param
	 * @param request
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value="/SaveUserMst.do" , method = RequestMethod.POST)
	public String saveUserMst(@RequestBody UserDTO param, HttpServletRequest request, ModelMap model) throws Exception {
		CommonResult result = userMstService.saveUserMst(param, request);
		
		model.addAttribute(JSON_STR, result);
		
		return JSON_VIEW;
	}
		
}
