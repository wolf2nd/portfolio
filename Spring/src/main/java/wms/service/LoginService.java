package wms.service;

import javax.servlet.http.HttpServletRequest;

import wms.dto.UserMstListResult;
import wms.dto.LoginParam;

public interface LoginService {
	public UserMstListResult selectUserInfo(LoginParam param, HttpServletRequest request) throws Exception;
}
