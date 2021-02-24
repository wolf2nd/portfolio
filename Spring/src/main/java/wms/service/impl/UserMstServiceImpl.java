package wms.service.impl;

import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import wms.cmmn.ExcelUtil;
import wms.cmmn.SHAPasswordEncoder;
import wms.dto.AreaMstParam;
import wms.dto.CodeMstDTO;
import wms.dto.CodeMstListResult;
import wms.dto.UserDTO;
import wms.dto.UserMenuDTO;
import wms.dto.UserMenuListResult;
import wms.dto.UserMstListResult;
import wms.dto.UserMstParam;
import wms.dto.CommonResult;
import wms.mapper.UserMstMapper;
import wms.service.UserMstService;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service("userMstService")
public class UserMstServiceImpl implements UserMstService {

  @Resource(name = "userMstMapper")
  private UserMstMapper userMstMapper;

  /**
   * 사용자 정보의 검색 처리
   */
  public UserMstListResult selectUserMstList(UserMstParam param, HttpServletRequest request) throws Exception {
    List<UserDTO> userMstList = userMstMapper.selectUserMstList(param);

    UserMstListResult result = new UserMstListResult();
    result.setUserMstList(userMstList);

    return result;
  }
  
    /**
   * 사용자정보 취득
   */
  public UserMstListResult selectUserMst(UserMstParam param, HttpServletRequest request) throws Exception {

    List<UserDTO> userMstList = userMstMapper.selectUserInfo(param);

    UserMstListResult result = new UserMstListResult();
    result.setUserMstList(userMstList);

    return result;
  }

  /**
   * 사용자 정보의 삭제 처리
   */
  public CommonResult deleteUserMst(List<UserMstParam> paramList, HttpServletRequest request) throws Exception {

    for (int i = 0; i < paramList.size(); i++) {
      UserMstParam param = paramList.get(i);
      userMstMapper.deleteUserMst(param.getUserId());
    }

    return new CommonResult();
  }

  /**
   * 사용자 정보의 저장 처리
   */
  public CommonResult saveUserMst(UserDTO param, HttpServletRequest request) throws Exception {

    SHAPasswordEncoder shaPasswordEncoder = new SHAPasswordEncoder(256);
    param.setPassWd(shaPasswordEncoder.encode(param.getPassWd()));

    if (param.getEditFlag().equals("P") == true) {
      userMstMapper.chagePassword(param);
    } else {
      int upd_cnt = -1;
      try {
        upd_cnt = userMstMapper.updateUserMst(param);
        if (!param.getFromLoc().equals("") && !param.getToLoc().equals("")) {
          userMstMapper.clearSurveyLoc(param);
          userMstMapper.updateSurveyLoc(param);
        }
      } catch (Exception ex) {
        throw new Exception(ex);
      }

      if (upd_cnt == 0) {
        userMstMapper.insertUserMst(param);
      }
    }
    return new CommonResult();
  }

}
