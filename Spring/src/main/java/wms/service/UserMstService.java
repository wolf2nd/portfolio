package wms.service;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import wms.dto.UserDTO;
import wms.dto.UserMenuDTO;
import wms.dto.UserMenuListResult;
import wms.dto.UserMstListResult;
import wms.dto.UserMstParam;
import wms.dto.CommonResult;

public interface UserMstService {
	/** 사용자 정보 마스터 **/
	UserMstListResult selectUserMstList(UserMstParam param, HttpServletRequest request) throws Exception;
	UserMstListResult selectUserMst(UserMstParam param, HttpServletRequest request) throws Exception;
	CommonResult deleteUserMst(List<UserMstParam> paramList, HttpServletRequest request) throws Exception;
	CommonResult saveUserMst(UserDTO param, HttpServletRequest request) throws Exception;	
}
