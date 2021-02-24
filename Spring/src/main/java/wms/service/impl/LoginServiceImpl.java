package wms.service.impl;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Service;

import wms.cmmn.CommonProperties.SESSION_ID;
import wms.cmmn.ConfigProperties;
import wms.cmmn.SHAPasswordEncoder;
import wms.dto.LoginParam;
import wms.dto.UserDTO;
import wms.dto.UserMstListResult;
import wms.dto.UserMenuListResult;
import wms.service.LoginService;
import wms.service.MainService;
import wms.mapper.LoginMapper;

@Service("loginService")
public class LoginServiceImpl implements LoginService {
  
  @Resource(name = "loginMapper")
  private LoginMapper loginMapper;
  
  @Resource(name="mainService") 
  public MainService mainService;

  public UserMstListResult selectUserInfo(LoginParam param, HttpServletRequest request) throws Exception {
    UserMstListResult result = new UserMstListResult();
    String strInputPw = param.getPasswd();
    String strComparePw = "";
    String strWarning = "비밀번호가 일치하지 않습니다. 로그인 실패허용횟수 5회/ ";
	private static int attemptLimit = 5;
    int attemptCnt = 0;
    
    SHAPasswordEncoder shaPasswordEncoder = new SHAPasswordEncoder(256);
    strInputPw = shaPasswordEncoder.encode(strInputPw);
    
    UserDTO userInfo = loginMapper.selectUserInfo(param);
    
    // 사용자 정보 검색결과 없음.
    if(userInfo == null) {
      result.setResult("1", ConfigProperties.getConfigProperty("ERR_MSG.INVALID_LOGIN"));
      return result;
    } else {
      result.setUserMstDto(userInfo);
      strComparePw = userInfo.getPassWd();
      
      // 사용자ID 잠금상태일 경우.
      if(userInfo.getExpiryKbn().equals("1")){
        result.setResult("1", "사용이 제한된 사용자ID입니다.");
        return result;
        // 비밀번호 불일치.
      } else if(!strComparePw.equals(strInputPw)) {
        attemptCnt = userInfo.getAttemptCount() + 1;
        userInfo.setAttemptCount(attemptCnt);
        
        if(attemptCnt >= attemptLimit){
          userInfo.setExpiryKbn("1");
          strWarning = strWarning + attemptCnt + "\n 사용자ID가 잠금상태로 변경됩니다.";
          result.setResult("1", strWarning);
          loginMapper.updateLoginAttempt(userInfo);
        } else {
          result.setResult("1", strWarning + attemptCnt);
          loginMapper.updateLoginAttempt(userInfo);
        }
        return result;
        // 패스워드 변경일자 3개월 지났을경우.
      } else if(userInfo.getAvailableFlag().equals("Y")) {
        result.setErrCd("password");
        return result;
      } else {
        // 로그인 성공시
        // 타임아웃 milliseq
        int timeout = Integer.parseInt(ConfigProperties.getConfigProperty("TIMEOUT_SEQ"));
        userInfo.setAttemptCount(attemptCnt);
        
        UserMenuListResult menuResult = mainService.selectMenuList(userInfo.getUserId(), request);
        
        // 세션에 유저 정보 설정
        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(timeout);
        session.setAttribute(ConfigProperties.getConfigProperty("SESSION_NAME") , userInfo);
        session.setAttribute(userInfo.getUserId(), request.getRemoteAddr());
        session.setAttribute(SESSION_ID.USER_ID, userInfo.getUserId());// 로그 작성용 로그인 유저 아이디
        session.setAttribute(SESSION_ID.USER_NAME, userInfo.getUserName());// 화면 표시용 로그인 유저 이름
        session.setAttribute(SESSION_ID.LABEL_PRINTER, userInfo.getLabelPrinter());// 유저별 라벨프린터
        session.setAttribute(SESSION_ID.INVC_PRINTER, userInfo.getInvoicePrinter());// 유저별 송장프린터
        session.setAttribute(SESSION_ID.A4_PRINTER, userInfo.getA4Printer());// 유저별 A4용지 프린터
        session.setAttribute("MENU_LIST", menuResult);// 메뉴리스트
        
        loginMapper.updateLoginAttempt(userInfo);
      }
    }
    
    return result;
  }

}
