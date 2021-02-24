package wms.service.task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import wms.cmmn.UtilData;
import wms.dto.CommonResult;
import wms.dto.MoveLocationDTO;
import wms.dto.MoveLocationParam;
import wms.dto.ProductDTO;
import wms.dto.ProductParam;
import wms.mapper.MoveLocationMapper;
import wms.mapper.ProductMapper;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_CLIENTDATA;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_CLIENTDATAX;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_HEADDATA;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_INPUT;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_PLANTDATA;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_PLANTDATAX;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_SALESDATA;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_SALESDATAX;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_STORAGELOCATIONDATA;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.MAT_SAVE_STORAGELOCATIONDATAX;
import wms.sap.dto.BAPI_MATERIAL_SAVEDATA.resMAT_SAVE;
import wms.sap.dto.BAPI_PO_CHANGE.PO_CHANGE_POITEM;
import wms.sap.dto.BAPI_PO_CHANGE.PO_CHANGE_POITEMX;
import wms.sap.dto.BAPI_PO_CHANGE.reqPO_CHANGE;
import wms.sap.dto.BAPI_PO_CHANGE.resPO_CHANGE;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.ORDER_HEADER_INX;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.ORDER_ITEM_IN;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.ORDER_ITEM_INX;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.SALE_DOC_INPUT;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.SALE_DOC_TABLE;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.reqSALEDOC_CHANGE;
import wms.sap.dto.BAPI_SD_SALESDOCUMENT_CHANGE.resSALEDOC_CHANGE;
import wms.sap.dto.RFC_READ_TABLE.READ_TABLE_FIELDS;
import wms.sap.dto.RFC_READ_TABLE.READ_TABLE_OPTIONS;
import wms.sap.dto.RFC_READ_TABLE.RFC_READ_TABLE_INPUT;
import wms.sap.dto.RFC_READ_TABLE.RFC_READ_TABLE_PARAM;
import wms.sap.dto.RFC_READ_TABLE.RFC_READ_TABLE_TABLE;
import wms.sap.dto.RFC_READ_TABLE.resRFC_READ_TABLE;
import wms.sap.sap_service.BapiSapService;
import wms.service.MoveLocationService;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;

@Service("naviBatchTask")
public class NaviBatchTask implements Job {
  private ProductMapper       productMapper;

  private MoveLocationMapper  moveLocationMapper;

  private BapiSapService      sapService;

  private MoveLocationService moveLocationService;

  private final Logger        logger = LoggerFactory.getLogger(NaviBatchTask.class);

  @SuppressWarnings("rawtypes")
  @Override
  /**
   * Quartz 이용한 배치실행 로직
   * @param param
   * @return
   * @throws Exception
   */
  public void execute(JobExecutionContext context) throws JobExecutionException {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    String jobNameStr = "STORAGETYPE_CHANGE";
    String defineNameStr = "저장위치 변환";

    StringBuffer sbLog = new StringBuffer();
    sbLog.append("Executing task...").append("\n");
    sbLog.append("[Start Task]").append(")\n");
    sbLog.append("▶ JOB      :【").append(jobNameStr).append("】\n");
    sbLog.append("▶ JOB 설명 :【").append(defineNameStr).append("】\n");
    sbLog.append("   [Prepare    ").append(sdf.format(System.currentTimeMillis())).append("] ");
    // sbLog.append("[Stop Task]");
    logger.info(sbLog.toString());

    try {
      this.getBeansFromContext(context);

      // 1.저장위치 변경할 상품정보 취득 (UPDATE_DATE기준 현재- 2일)
      List<ProductDTO> productList = productMapper.selectStorageTypeChangeList();
      if (productList.size() == 0) {
        throw new RuntimeException("변경 처리할 데이터가 없습니다.");
      }

      for (int i = 0; i < productList.size(); i++) {
        ProductDTO productParam = productList.get(i);
        
        // 2.자재마스터 저장위치 변경
        changepPImasterStoragetype(productParam);
        
        // 3.재고 저장위치 이동
        execMoveStock(productParam);

        // 4-1.미입고 구매오더 조회
        resRFC_READ_TABLE selectInstockRes = selectInstockAwaitList(productParam);

        if (selectInstockRes.getResultMsg().equals("ZERO")) {
        } else {
          // 4-2.구매오더 변경 로직
           changeInstockStoragetype(selectInstockRes.getDATA(), productParam);
        }

        // 5-1.납품전표 미생성 판매오더 조회
        resRFC_READ_TABLE selectOutstockRes = selectOutstockAwaitList(productParam);
        if (selectOutstockRes.getResultMsg().equals("ZERO")) {
        } else {
          // 5-2.판매오더 변경 로직
           changeOutstockStoragetype(selectOutstockRes.getDATA(), productParam);
        }
      }

    } catch (Exception e) {
      logger.error(jobNameStr + "[BATCH] Error", e);
    }
  }

  /**
   * 구매오더 조회 RFC_READ_TABLE
   * 
   * @param param
   * @return
   * @throws Exception
   */
  public resRFC_READ_TABLE selectInstockAwaitList(ProductDTO productParam) throws Exception {
    resRFC_READ_TABLE result = new resRFC_READ_TABLE();
    final String errMsg = "[BATCH] 구매오더 조회 Error -->";

    try {
      RFC_READ_TABLE_INPUT inputRT = new RFC_READ_TABLE_INPUT();
      inputRT.setQUERY_TABLE("EKPO"); // 구매오더 테이블

      READ_TABLE_FIELDS fieldName1 = new READ_TABLE_FIELDS();
      READ_TABLE_FIELDS fieldName2 = new READ_TABLE_FIELDS();

      fieldName1.setFIELDNAME("EBELN"); // 구매오더번호
      fieldName2.setFIELDNAME("EBELP"); // 구매오더명세

      READ_TABLE_OPTIONS text1 = new READ_TABLE_OPTIONS();
      READ_TABLE_OPTIONS text2 = new READ_TABLE_OPTIONS();

      text1.setTEXT("MATNR = '" + productParam.getItemno() + "' AND PRDAT > '" + UtilData.getDate(-2, 0, "yyyyMMdd")
        + "' AND LGORT = '" + productParam.getLgfsb() + "'");

      text2.setTEXT("AND ELIKZ <> 'X' AND LOEKZ <> 'L'");

      // ---------------------------------------------------------------------
      List<READ_TABLE_FIELDS> fieldsList = new ArrayList<READ_TABLE_FIELDS>();
      fieldsList.add(fieldName1);
      fieldsList.add(fieldName2);

      List<READ_TABLE_OPTIONS> textList = new ArrayList<READ_TABLE_OPTIONS>();
      textList.add(text1);
      textList.add(text2);

      // ------------------------------------------------------------------------
      RFC_READ_TABLE_TABLE tableRT = new RFC_READ_TABLE_TABLE();
      tableRT.setFields(fieldsList);
      tableRT.setOptions(textList);

      RFC_READ_TABLE_PARAM param = new RFC_READ_TABLE_PARAM();
      param.setInput(inputRT);
      param.setTable(tableRT);

      result = sapService.FuncREAD_TABLE(param);
      Thread.sleep(200);

    } catch (Exception e) {
      result.setResultMsg("SAP 통신중 에러가 발생했습니다.\n" + e.getMessage());
      logger.error(errMsg, productParam.getItemno() + "-" + result.getResultMsg());
    }

    return result;
  }

  /**
   * 판매오더 조회 RFC_READ_TABLE
   * 
   * @param param
   * @return
   * @throws Exception
   */
  public resRFC_READ_TABLE selectOutstockAwaitList(ProductDTO productParam) throws Exception {
    resRFC_READ_TABLE result = new resRFC_READ_TABLE();
    final String errMsg = "[BATCH] 판매오더 조회 Error -->";

    try {
      RFC_READ_TABLE_INPUT inputRT = new RFC_READ_TABLE_INPUT();
      inputRT.setQUERY_TABLE("VBBE"); // 납품전표 미생성 판매오더 테이블

      READ_TABLE_FIELDS fieldName1 = new READ_TABLE_FIELDS();
      READ_TABLE_FIELDS fieldName2 = new READ_TABLE_FIELDS();

      fieldName1.setFIELDNAME("VBELN"); // 판매오더번호
      fieldName2.setFIELDNAME("POSNR"); // 판매오더명세

      READ_TABLE_OPTIONS text1 = new READ_TABLE_OPTIONS();
      READ_TABLE_OPTIONS text2 = new READ_TABLE_OPTIONS();

      text1.setTEXT("MATNR = '" + productParam.getItemno() 
        + "' AND MBDAT > '" + UtilData.getDate(-1, 0, "yyyyMMdd")+ "'");
      text2.setTEXT("AND AUART <> 'ZTR1' AND AUART <> 'ZTR3' AND VBTYP <> 'J'");

      // ---------------------------------------------------------------------
      List<READ_TABLE_FIELDS> fieldsList = new ArrayList<READ_TABLE_FIELDS>();
      fieldsList.add(fieldName1);
      fieldsList.add(fieldName2);

      List<READ_TABLE_OPTIONS> textList = new ArrayList<READ_TABLE_OPTIONS>();
      textList.add(text1);
      textList.add(text2);

      // ------------------------------------------------------------------------
      RFC_READ_TABLE_TABLE tableRT = new RFC_READ_TABLE_TABLE();
      tableRT.setFields(fieldsList);
      tableRT.setOptions(textList);

      RFC_READ_TABLE_PARAM param = new RFC_READ_TABLE_PARAM();
      param.setInput(inputRT);
      param.setTable(tableRT);

      result = sapService.FuncREAD_TABLE(param);
      Thread.sleep(200);

    } catch (Exception e) {
      result.setResultMsg("SAP 통신중 에러가 발생했습니다.\n" + e.getMessage());
      logger.error(errMsg, productParam.getItemno() + "-" + result.getResultMsg());
    }

    return result;
  }

  /**
   * 구매오더 변경 BAPI_PO_CHANGE
   * 
   * @param param
   * @return
   * @throws Exception
   */
  private CommonResult changeInstockStoragetype(List<Map<String, Object>> listMapParam, ProductDTO productDto) {
    CommonResult result = new CommonResult();
    final String errMsg = "[BATCH] 구매오더 변경 Error -->";

    try {

      String highcubeFlag = productDto.getOutBoxFlg();

      for (int i = 0; i < listMapParam.size(); i++) {
        Map<String, Object> mapParam = listMapParam.get(i);

        reqPO_CHANGE reqParam = new reqPO_CHANGE();
        List<PO_CHANGE_POITEM> poitemList = new ArrayList<PO_CHANGE_POITEM>();
        List<PO_CHANGE_POITEMX> poitemxList = new ArrayList<PO_CHANGE_POITEMX>();

        reqParam.setPURCHASEORDER(UtilData.lpad((String)mapParam.get("EBELN"), 10, "0")); // 구매오더번호

        PO_CHANGE_POITEM poitem = new PO_CHANGE_POITEM();
        poitem.setPO_ITEM(UtilData.lpad((String)mapParam.get("EBELP"), 5, "0"));
        poitem.setMATERIAL(productDto.getItemno());
        if (highcubeFlag.equals("1")) {
          poitem.setSTGE_LOC("K1S1");
        } else {
          poitem.setSTGE_LOC("K1P1");
        }
        poitemList.add(poitem);

        PO_CHANGE_POITEMX poitemx = new PO_CHANGE_POITEMX();
        poitemx.setPO_ITEM(UtilData.lpad((String)mapParam.get("EBELP"), 5, "0"));
        poitemx.setMATERIAL("X");
        poitemx.setSTGE_LOC("X");
        poitemx.setPO_ITEMX("X");
        poitemxList.add(poitemx);

        reqParam.setPOITEM(poitemList);
        reqParam.setPOITEMX(poitemxList);

        resPO_CHANGE sapRes = sapService.FuncPO_STORAGETYPE_CHANGE(reqParam);
        Thread.sleep(200);

        if (sapRes == null) {
          result.setErrMsg("SAP Return 데이터가 존재하지않습니다.");
          logger.error(errMsg, productDto.getItemno() + "-" + result.getErrMsg());
        } else {

          if (!sapRes.getResultCode().equals("00")) {
            result.setResult(sapRes.getResultCode(), sapRes.getResultMsg());
            logger.error(errMsg, productDto.getItemno() + "-" + sapRes.getResultMsg());
          }
        }
      }
    } catch (Exception e) {
      logger.error(errMsg, e);
    }

    return result;
  }

  /**
   * 판매오더 변경 BAPI_SALESORDER_CHANGE
   * 
   * @param param
   * @return
   * @throws Exception
   */
  private CommonResult changeOutstockStoragetype(List<Map<String, Object>> listMapParam, ProductDTO productDto) {
    CommonResult result = new CommonResult();
    final String errMsg = "[BATCH] 판매오더 변경 Error -->";

    try {

      String highcubeFlag = productDto.getOutBoxFlg();

      for (int i = 0; i < listMapParam.size(); i++) {
        Map<String, Object> mapParam = listMapParam.get(i);

        ORDER_HEADER_INX orderHeaderInx = new ORDER_HEADER_INX();
        orderHeaderInx.setUPDATEFLAG("U");

        SALE_DOC_INPUT saleDocInput = new SALE_DOC_INPUT();
        saleDocInput.setSALESDOCUMENT(UtilData.lpad((String)mapParam.get("VBELN"), 10, "0")); // 판매오더번호
        saleDocInput.setHeader_inx(orderHeaderInx);

        ORDER_ITEM_IN orderitem = new ORDER_ITEM_IN();
        List<ORDER_ITEM_IN> orderItemList = new ArrayList<ORDER_ITEM_IN>();
        orderitem.setITM_NUMBER(UtilData.lpad((String)mapParam.get("POSNR"), 6, "0"));
        if (highcubeFlag.equals("1")) {
          orderitem.setSTORE_LOC("K1S1");
        } else {
          orderitem.setSTORE_LOC("K1P1");
        }
        orderitem.setITEM_CATEG("ZTN1");
        orderItemList.add(orderitem);

        ORDER_ITEM_INX orderitemx = new ORDER_ITEM_INX();
        List<ORDER_ITEM_INX> orderItemXList = new ArrayList<ORDER_ITEM_INX>();
        orderitemx.setITM_NUMBER(UtilData.lpad((String)mapParam.get("POSNR"), 6, "0"));
        orderitemx.setUPDATEFLAG("U");
        orderitemx.setSTORE_LOC("X");
        orderitemx.setITEM_CATEG("X");
        orderItemXList.add(orderitemx);

        SALE_DOC_TABLE saleDocTable = new SALE_DOC_TABLE();
        saleDocTable.setORDERITEM(orderItemList);
        saleDocTable.setORDERITEMX(orderItemXList);

        reqSALEDOC_CHANGE reqParam = new reqSALEDOC_CHANGE();
        reqParam.setSALEDOCINPUT(saleDocInput);
        reqParam.setSALEDOCTABLE(saleDocTable);

        resSALEDOC_CHANGE sapRes = sapService.FuncSD_SALESORDER_CHANGE(reqParam);
        Thread.sleep(200);

        if (sapRes == null) {
          result.setErrMsg("SAP Return 데이터가 존재하지않습니다.");
          logger.error(errMsg, productDto.getItemno() + "-" + result.getErrMsg());
        } else {

          if (!sapRes.getResultCode().equals("00")) {
            result.setResult(sapRes.getResultCode(), sapRes.getResultMsg());
            logger.error(errMsg, productDto.getItemno() + "-" + sapRes.getResultMsg());
          }
        }
      }
    } catch (Exception e) {
      logger.error(errMsg, e);
    }

    return result;
  }

  /**
   * 자재마스터 저장위치 변경 BAPI_MATERIAL_SAVEDATA
   * 
   * @param param
   * @return
   * @throws Exception
   */
  private CommonResult changepPImasterStoragetype(ProductDTO productParam) {
    CommonResult result = new CommonResult();
    final String errMsg = "[BATCH] 자재마스터 저장위치 변경 Error -->";

    try {
      String itemCat = "";
      String storConds = "";
      String stgeLoc = "";

      if (productParam.getOutBoxFlg().equals("1")) {
        itemCat = "NRM1";
        storConds = "S1";
        stgeLoc = "K1S1";
      } else {
        itemCat = "NORM";
        storConds = "P1";
        stgeLoc = "K1P1";
      }

      MAT_SAVE_HEADDATA headData = new MAT_SAVE_HEADDATA();
      headData.setMATERIAL(productParam.getItemno());

      MAT_SAVE_CLIENTDATA clientData = new MAT_SAVE_CLIENTDATA();
      clientData.setITEM_CAT(itemCat);
      clientData.setSTOR_CONDS(storConds);

      MAT_SAVE_CLIENTDATAX clientDataX = new MAT_SAVE_CLIENTDATAX();
      clientDataX.setITEM_CAT("X");
      clientDataX.setSTOR_CONDS("X");

      MAT_SAVE_SALESDATA salesData = new MAT_SAVE_SALESDATA();
      salesData.setSALES_ORG("MKR1");
      salesData.setDISTR_CHAN("KR");
      salesData.setITEM_CAT(itemCat);

      MAT_SAVE_SALESDATAX salesDataX = new MAT_SAVE_SALESDATAX();
      salesDataX.setSALES_ORG("MKR1");
      salesDataX.setDISTR_CHAN("KR");
      salesDataX.setITEM_CAT("X");

      MAT_SAVE_PLANTDATA planData = new MAT_SAVE_PLANTDATA();
      planData.setPLANT("MKR1");
      planData.setSLOC_EXPRC(stgeLoc);

      MAT_SAVE_PLANTDATAX planDataX = new MAT_SAVE_PLANTDATAX();
      planDataX.setPLANT("MKR1");
      planDataX.setSLOC_EXPRC("X");

      MAT_SAVE_STORAGELOCATIONDATA stgeLocData = new MAT_SAVE_STORAGELOCATIONDATA();
      stgeLocData.setPLANT("MKR1");
      stgeLocData.setSTGE_LOC(stgeLoc);
      stgeLocData.setSTGE_BIN("");

      MAT_SAVE_STORAGELOCATIONDATAX stgeLocDataX = new MAT_SAVE_STORAGELOCATIONDATAX();
      stgeLocDataX.setPLANT("MKR1");
      stgeLocDataX.setSTGE_LOC(stgeLoc);
      stgeLocDataX.setSTGE_BIN("X");

      // input data
      MAT_SAVE_INPUT input = new MAT_SAVE_INPUT();
      input.setHeaddata(headData);
      input.setClientdata(clientData);
      input.setCliendataX(clientDataX);
      input.setSalesdata(salesData);
      input.setSalesdataX(salesDataX);
      input.setPlantdata(planData);
      input.setPlantdataX(planDataX);
      input.setStoragedata(stgeLocData);
      input.setStoragedataX(stgeLocDataX);

      resMAT_SAVE sapRes = sapService.FuncMATERIAL_STORAGETYPE_CHANGE(input);
      Thread.sleep(200);
      
      if (sapRes == null) {
        result.setErrMsg("SAP Return 데이터가 존재하지않습니다.");
        logger.error(errMsg, productParam.getItemno() + "-" + result.getErrMsg());
      } else {

        if (!sapRes.getResultCode().equals("00")) {
          result.setResult(sapRes.getResultCode(), sapRes.getResultMsg());
          logger.error(errMsg, productParam.getItemno() + "-" + sapRes.getResultMsg());
        }
      }
    } catch (Exception e) {
      logger.error(errMsg, e);
    }

    return result;
  }

  /**
   * 저장위치간 전송 호출 MoveLocationServiceImpl.executeMoveLocation
   * 
   * @param param
   * @return
   * @throws Exception
   */
  private CommonResult execMoveStock(ProductDTO productDto) {
    CommonResult result = new CommonResult();
    final String errMsg = "[BATCH] 저장위치간 전송 Error -->";
    SimpleDateFormat dateSdf = new SimpleDateFormat("yyyyMMdd");
    Date toDay = new Date();

    try {

      String highcubeFlag = productDto.getOutBoxFlg();
      String stgeLoc = "";
      String stockType = "";

      if (highcubeFlag.equals("1")) {
        stgeLoc = "K1S1";
        stockType = "4";
      } else {
        stgeLoc = "K1P1";
        stockType = "1";
      }

      MoveLocationParam moveLocationParam = new MoveLocationParam();
      moveLocationParam.setItemNo(productDto.getItemno());
      moveLocationParam.setItemName(productDto.getProductname());

      List<MoveLocationDTO> moveLocationList = moveLocationMapper.selectMoveLocationbfList(moveLocationParam);

      if (moveLocationList.size() <= 0) {
        result.setErrMsg("재고 데이터가 존재하지않습니다.");
        logger.error(errMsg, result.getErrMsg());
      } else {
        for (MoveLocationDTO n : moveLocationList) {
          moveLocationParam.setMoveKubun("2");
          moveLocationParam.setLocationNo(n.getLocationNo());
          moveLocationParam.setAfLocationNo(n.getLocationNo());
          moveLocationParam.setStorageType(n.getStorageType());
          moveLocationParam.setAfStorageType(stgeLoc);
          moveLocationParam.setMoveNum(Integer.parseInt(n.getStockNum()));
          moveLocationParam.setDate(dateSdf.format(toDay));
          moveLocationParam.setUserId("SYSTEM");
          moveLocationParam.setUserName("시스템");

          result = moveLocationService.executeMoveLocation(moveLocationParam, null);
          Thread.sleep(200);
          if (!result.getErrCd().equals("0")) {
            logger.error(errMsg, result.getErrMsg());
            continue;
          }
        }
      }
      
      // pm_item 마스터의 stock_type UPDATE
      ProductParam productParam = new ProductParam();
      
      productParam.setItemno(productDto.getItemno());
      productParam.setAttrValue(stockType);
      
      productMapper.updateProductStockType(productParam);

      return result;

    } catch (Exception e) {
      logger.error(errMsg, e);
    }

    return result;
  }

  /**
   * 비지니스로직 구현시 필요한 Service 부분 명시
   * 
   * @param param
   * @return
   * @throws Exception
   */
  private void getBeansFromContext(JobExecutionContext context) throws SchedulerException {
    ApplicationContext applicationContext = (ApplicationContext)context.getScheduler().getContext()
      .get("applicationContext");
    this.productMapper = applicationContext.getBean(ProductMapper.class);
    this.sapService = applicationContext.getBean(BapiSapService.class);
    this.moveLocationService = applicationContext.getBean(MoveLocationService.class);
    this.moveLocationMapper = applicationContext.getBean(MoveLocationMapper.class);
  }
}
