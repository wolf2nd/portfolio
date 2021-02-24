package com.pda.navimpda

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_totalpicking.*
import org.json.JSONArray
import org.json.JSONException

/**
 * Created by Administrator on 2016-08-08.
 * 품목 피킹 메뉴
 */
class PickingActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var listview: ListView
    private lateinit var adapter: ListViewAdapter
    private lateinit var a_type: String
    private lateinit var ed_loc_scan: EditText
    private lateinit var imm: InputMethodManager
    private lateinit var prgDlg: ProgressDialog
    private lateinit var zone_code: String
    private val spinner: Spinner? = null
    private var bUpdate_flag: Boolean? = null
    private var m_nPID: Int = 0

    internal var ssocket: Red_Socket? = null
    internal var m_Receiver: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this.getIntent())

        m_nPID = intent.getIntExtra("PID", R.id.bt_i2)
        setContentView(R.layout.activity_totalpicking)

        val myButtonLayout = getLayoutInflater().inflate(R.layout.top_bar, null)
        val ab = supportActionBar
        ab?.customView = myButtonLayout
        ab?.displayOptions = (ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_SHOW_CUSTOM)
        ab?.setBackgroundDrawable(ColorDrawable(Color.argb(255, 1, 64, 81)))

        setContent("", "", "", "")

        // 리스트뷰 참조 및 Adapter달기
        listview = lv_picking_list
        adapter = ListViewAdapter(R.layout.listview_picking_item)
        listview!!.setAdapter(adapter)
        listview!!.setVisibility(View.VISIBLE)
        listview!!.setItemsCanFocus(false)
        listview!!.setChoiceMode(ListView.CHOICE_MODE_SINGLE)

        bt_reg.setOnClickListener(this)
        bt_find.setOnClickListener(this)
        bt_print.setOnClickListener(this)
        bt_pick_done.setOnClickListener(this)
        bt_qty_hold.setOnClickListener(this)
        bt_status_hold.setOnClickListener(this)
        bt_pick_done.setEnabled(false)
        bt_qty_hold.setEnabled(false)
        bt_status_hold.setEnabled(false)

        bUpdate_flag = false
        prgDlg = ProgressDialog.show(this@PickingActivity, clsGlobalDefine.FIND_TITLE, clsGlobalDefine.MSG_WAITING, true, true)
        procedure_call("PICKING_ZONE")

        listview.onItemClickListener = object : AdapterView.OnItemClickListener{
            override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val checkedItem = parent.getItemAtPosition(position) as ListViewItem
                getCheckedRow(checkedItem)
            }
        }

        SetKeyPress() // Key Event 발생
    }


    // 사용자ID 표시, 공통Button 에대한 Event 설정
    fun setContent(sPrg1: String, sPrg2: String, sPrg3: String, sPrg4: String) {
        clsCommon.SetTextViewData(this, R.id.tx_title, "단행피킹")
        clsCommon.SetTextViewData(this, R.id.tx_user, clsGlobalDefine.user_name + "(" + clsGlobalDefine.userid + ")")

        val bt_home = findViewById(R.id.btn_home) as ImageButton
        bt_home.setOnClickListener(this)
        val bt_logout = findViewById(R.id.btn_logout) as ImageButton
        bt_logout.setOnClickListener(this)
        if (clsGlobalDefine.BT_BACK_ENABLE == 1) {
            val bt_back = findViewById(R.id.bt_back) as ImageButton
            bt_back.setOnClickListener(this)
        }
        // 하단의 메뉴버튼
        val bt_prg1 = findViewById(R.id.bt_prg1) as Button
        bt_prg1.setOnClickListener(this)
        bt_prg1.setText(sPrg1)

        clsCommon.SetButtonData(this, R.id.bt_prg2, "")
        clsCommon.SetButtonData(this, R.id.bt_prg3, "")
        clsCommon.SetButtonData(this, R.id.bt_prg4, "")

        // Keyboard 제어
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        SetHandler()
    }

    // 검색 창 Key Press
    fun SetKeyPress() {
        ed_loc_scan = findViewById(R.id.ed_loc_scan) as EditText
        imm.hideSoftInputFromWindow(ed_loc_scan.windowToken, 0) // hide keyboard
        ed_loc_scan.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    imm!!.hideSoftInputFromWindow(ed_loc_scan.windowToken, 0) // hide keyboard
                    if (ed_loc_scan.getText().toString().length == 0) {
                        basicAlert(ed_loc_scan)
//                        clsCommon.setCustomInfo(this@PickingActivity, "로케이션코드를 스캔하세요.")
                        clsCommon.SoundPlay(this@PickingActivity, R.raw.wrong)
                        return false
                    }
                    //상품스캔시 리스트 index찾기
                    checkListviewByScan(ed_loc_scan.text.toString().trim())
                    ed_loc_scan!!.requestFocus()
                    ed_loc_scan!!.text.clear()
                }
                return false
            }
        })
    }

    // 스캔한 값으로 리스트뷰 선택
    fun checkListviewByScan(scanValue: String) {
        for (n in 0 until listview.count) {
            val tempViewRow = listview.getItemAtPosition(n) as ListViewItem
            val compareStr = tempViewRow.getField(clsFieldDefine.location_no) as String
            if (scanValue.equals(compareStr)) {
                val checkedItem = listview.getItemAtPosition(n) as ListViewItem
                listview.setItemChecked(n, true)
                getCheckedRow(checkedItem)
                return
            }
        }
        basicAlert(ed_loc_scan)
//        clsCommon.setCustomInfo(this@PickingActivity, "리스트에 존재하지 않는 로케이션입니다.")
        clsCommon.SoundPlay(this@PickingActivity, R.raw.wrong)
    }

    fun basicAlert(view: View){
        val builder = AlertDialog.Builder(this@PickingActivity)
        with(builder)
        {
            builder.setTitle("단행피킹 작업경고")
            builder.setMessage("존재하지 않는 로케이션입니다.")
            builder.setPositiveButton(android.R.string.yes){dialog, which ->
            }
            builder.show()
        }
    }

    // 선택한 리스트 row 값 가져오기
    fun getCheckedRow(checkedItem: ListViewItem) {
        displayPickStatus(checkedItem.getField(clsFieldDefine.yn_flag))
        tx_package_content.text = checkedItem.getField(clsFieldDefine.package_content)
        tx_option_code.text = checkedItem.getField(clsFieldDefine.option_code)
        tx_memo.text = checkedItem.getField(clsFieldDefine.memo)
        tx_qty.text = checkedItem.getField(clsFieldDefine.qty)
        tx_count.text = checkedItem.getField(clsFieldDefine.count)
        tx_stockqty.text = checkedItem.getField(clsFieldDefine.out_possible_num)
        tx_product_name.text = checkedItem.getField(clsFieldDefine.product_name)
        tx_caution_flg.text = checkedItem.getField(clsFieldDefine.caution_flg)
        tx_unit_caution_flag.text = checkedItem.getField(clsFieldDefine.unit_caution_flag)
        Glide.with(this).load(checkedItem.getField(clsFieldDefine.image)).into(iv_pic)
    }

    // 선택한 row 라벨 프린트
    fun printCheckedRow(checkedItem: ListViewItem) {
        val p_Item_no = checkedItem.getField(clsFieldDefine.item_no) as String
        val p_Product_name = checkedItem.getField(clsFieldDefine.product_name) as String
        val p_Option_code = checkedItem.getField(clsFieldDefine.option_code) as String
        val p_Package_content = checkedItem.getField(clsFieldDefine.package_content) as String
        val p_Storage_type = checkedItem.getField(clsFieldDefine.storage_type) as String
        val p_Location_no = checkedItem.getField(clsFieldDefine.location_no)
        val p_I_qty = checkedItem.getField(clsFieldDefine.i_qty) as String
        val p_Promotion_Memo = displayFlyerAndCatalog(checkedItem.getField(clsFieldDefine.flyer_flag), checkedItem.getField(clsFieldDefine.catalog_flag))

        MainActivity.printLabel(p_Item_no, p_Product_name, p_Option_code, p_Package_content, p_Storage_type, p_Location_no, p_I_qty, p_Promotion_Memo)
    }

    fun detailTextInfoClear() {
        listview.itemsCanFocus = false
        adapter.Clear()
        adapter.notifyDataSetChanged()
        zone_code = ""

        tx_package_content.text = ""
        tx_option_code.text = ""
        tx_memo.text = ""
        tx_qty.text = ""
        tx_count.text = ""
        tx_stockqty.text = ""
        tx_product_name.text = ""
        tx_caution_flg.text = ""
        tx_unit_caution_flag.text = ""

        iv_pic.setImageBitmap(null)
    }

    // 피킹작업 상태표시
    fun displayPickStatus(pickingStatus: String?) {
        when (pickingStatus) {
            "0" -> {
                tx_picking_status.text = "미등록"
                tx_picking_status.setTextColor(Color.parseColor("#FF2222"))
                bt_pick_done.setEnabled(false)
                bt_qty_hold.setEnabled(false)
                bt_status_hold.setEnabled(false)
                listview.setItemChecked(-1, true)
            }
            "1" -> {
                tx_picking_status.text = "피킹중"
                tx_picking_status.setTextColor(Color.parseColor("#EEEE00"))
                bt_pick_done.setEnabled(true)
                bt_qty_hold.setEnabled(true)
                bt_status_hold.setEnabled(true)
            }
            "2" -> {
                tx_picking_status.text = "피킹완료"
                tx_picking_status.setTextColor(Color.parseColor("#33FF33"))
                bt_pick_done.setEnabled(false)
                bt_qty_hold.setEnabled(false)
                bt_status_hold.setEnabled(false)
            }
            else -> {
                tx_picking_status.text = ""
            }
        }
    }

    // 판촉물 표시
    fun displayFlyerAndCatalog(flyerFlag: String?, catalogFlag: String?): String {
        var flyerName = ""
        var catalogName = ""
        var pbFlyerName = ""

        if(flyerFlag == "0"){
            flyerName = "일반전단지"
        }
        when (catalogFlag) {
            "0" -> {
                catalogName = "카탈로그"
                pbFlyerName = "PB전단지"
            }
            "2" -> {
                pbFlyerName = "PB전단지"
            }
        }

        val flyerList = listOf(flyerName, catalogName, pbFlyerName).filter { it != "" }
        return flyerList.joinToString(",")
    }

    fun setPositionByIndexSpinnerAdapter(context: Context, sp: Spinner?, nID: Int, ar: ArrayList<String>, indexValue: Int) {
        var sp = sp
        val sadapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, ar)
        sp = (context as Activity).findViewById(nID) as Spinner
        sadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.setAdapter(sadapter)
        if(indexValue != null){
            sp.setSelection(indexValue)
        }
    }

    //  Socket 통신 상태 및 Data Receive 통지 담당
    fun SetHandler() {

        m_Receiver = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(inputMessage: Message) {
                // super.handleMessage(msg);
                when (inputMessage.what) {
                    0  // 통신시 오류 일경우
                    -> {
                        // String msg = (String) inputMessage.obj;
                        Log.d("OUT", inputMessage.obj as String)
                        prgDlg.dismiss()
                        clsCommon.setCustomToast(this@PickingActivity, inputMessage.obj as String)
                    }
                    2// Red_Socket.MessageType.SIMSOCK_DATA :
                    -> {
                        val msg = inputMessage.obj as String
                        ItemInfo(msg)
                        Log.d("OUT", msg)
                    }

                    1, 3, 4->
                        Log.d("OUT", inputMessage.obj as String)
//                    3
//                    ->
//                        Log.d("OUT", inputMessage.obj as String)
//
//                    4 ->
//                        Log.d("OUT", inputMessage.obj as String)
                }
            }
        }
    }

    //  프로시져 Call 및 소켓 전송
    fun procedure_call(cmd: String) {
        var sp = ""
        val pick_zone = clsCommon.SelectSpinnerCode(this, spinner, R.id.sp_zone_code)
        zone_code = pick_zone
        when (cmd) {
            "PICKING_ZONE" -> {
                a_type = cmd
                sp = clsGlobalDefine.START_DATA + clsGlobalDefine.CALL + clsGlobalDefine.DBMS + "USP_CODE_LIST ( '" + a_type + "', '', '' )"
                ssocket = Red_Socket(clsGlobalDefine.ip_address, clsGlobalDefine.port, m_Receiver, sp)
                ssocket!!.start()
            }
            "INFO" -> {
                a_type = cmd
                sp = clsGlobalDefine.START_DATA + clsGlobalDefine.CALL + clsGlobalDefine.DBMS + "USP_GET_PICKING_INFO ( '" + a_type + "', '"  + pick_zone + "', '" + clsGlobalDefine.userid + "' )"
                ssocket = Red_Socket(clsGlobalDefine.ip_address, clsGlobalDefine.port, m_Receiver, sp)
                ssocket!!.start()
            }
            "INSERT" -> {
                a_type = cmd
                sp = clsGlobalDefine.START_DATA + clsGlobalDefine.CALL + clsGlobalDefine.DBMS + "USP_REG_PICKING_INFO ( '" + a_type + "', '" + pick_zone + "', '', '','" + clsGlobalDefine.userid + "' )"
                ssocket = Red_Socket(clsGlobalDefine.ip_address, clsGlobalDefine.port, m_Receiver, sp)
                ssocket!!.start()
            }
            "UPDATE", "HOLD_QTY", "HOLD_STATUS" -> {
                a_type = cmd
                val item = listview.getItemAtPosition(listview.checkedItemPosition) as ListViewItem
                var item_no = item.getField(clsFieldDefine.item_no)
                var location_no = item.getField(clsFieldDefine.location_no)

                sp = clsGlobalDefine.START_DATA + clsGlobalDefine.CALL + clsGlobalDefine.DBMS + "USP_REG_PICKING_INFO ( '" + a_type + "', '" + pick_zone + "', '" + item_no + "', '" + location_no + "','" + clsGlobalDefine.userid + "' )"
                ssocket = Red_Socket(clsGlobalDefine.ip_address, clsGlobalDefine.port, m_Receiver, sp)
                ssocket!!.start()
            }
        }//end-of-switch
    }

    // 데이타 수신 해서 리스트나 텍스트필드에 Set
    fun ItemInfo(msg: String) {
        try {
            val json = JSONArray(msg)

            if(a_type != "PICKING_ZONE"){
                if (clsGlobalDefine.getResult(json) == false) {
                    prgDlg.dismiss()
                    clsCommon.setCustomToast(this, clsGlobalDefine.result_messsage)
                    detailTextInfoClear()
                    return
                }
                if (json.length() == 0) {
                    prgDlg.dismiss()
                    clsCommon.setCustomToast(this, clsGlobalDefine.MSG_NODATA)
                    adapter.Clear()
                    adapter.notifyDataSetChanged()
                    detailTextInfoClear()
                    return
                }
            }

            when (a_type) {
                "PICKING_ZONE" -> {
                    a_type = ""
                    prgDlg.dismiss()
                    val arraylist = ArrayList<String>()
                    var arrIndex = 0

                    for (i in 0 until json.length()) {
                        val jsonObject = json.getJSONObject(i)
                        arraylist.add(jsonObject.getString("DETAIL_CODE"))
                        if(jsonObject.getString("PICK_ZONE") == zone_code) arrIndex = i
                    }
                    setPositionByIndexSpinnerAdapter(this, spinner, R.id.sp_zone_code, arraylist, arrIndex)
                    return
                }
                "INFO" -> {
                    listview.itemsCanFocus = false
                    adapter.Clear()
                    for (n in 0 until json.length()) {
                        val jsonObject = json.getJSONObject(n)
                        adapter.addPickItem(
                                jsonObject.getString("LOCATION_NO"),
                                jsonObject.getString("ITEM_NO"),
                                jsonObject.getString("PACKAGE_CONTENT"),
                                jsonObject.getString("OPTION_CODE"),
                                jsonObject.getString("PRODUCT_NAME"),
                                jsonObject.getString("MEMO"),
                                jsonObject.getString("IMAGE"),
                                jsonObject.getString("STOCK_QTY"),
                                jsonObject.getString("QTY"),
                                jsonObject.getString("I_QTY"),
                                jsonObject.getString("ORDER_CNT"),
                                jsonObject.getString("PICKING_STATUS"),
                                jsonObject.getString("STORAGE_TYPE"),
                                jsonObject.getString("CATALOG_FLAG"),
                                jsonObject.getString("FLYER_FLAG"),
                                jsonObject.getString("CAUTION_FLG"),
                                jsonObject.getString("UNIT_CAUTION_FLAG")
                        )
                        if (n == 0) displayPickStatus(jsonObject.getString("PICKING_STATUS"))
                    }
                    adapter.notifyDataSetChanged()

                    var checkedRowId = listview.checkedItemPosition

                    if (checkedRowId <= -1) {
                    } else if(checkedRowId + 1 >= listview.count){
                        val checkedItem = listview.getItemAtPosition(checkedRowId) as ListViewItem
                        getCheckedRow(checkedItem)
                    } else {
                        checkedRowId += 1
                        listview.setItemChecked(checkedRowId, true)
                        val checkedItem = listview.getItemAtPosition(checkedRowId) as ListViewItem
                        getCheckedRow(checkedItem)
                    }

                    clsCommon.GetEditFocus(this, R.id.ed_loc_scan)
                    imm.hideSoftInputFromWindow(ed_loc_scan.windowToken, 0)
                    prgDlg!!.dismiss()
                    procedure_call("PICKING_ZONE")
                }
                "INSERT", "UPDATE", "HOLD_QTY", "HOLD_STATUS" -> {
                    val jsonObj = json.getJSONObject(0)
                    if (!jsonObj.getString("RESULT_CD").equals("0")){
                        clsCommon.setCustomToast(this, jsonObj.getString("RESULT_MSG"))

                        val nChecked = listview.checkedItemPosition
                        if (nChecked > -1) {
                            val checkedItem = listview.getItemAtPosition(nChecked) as ListViewItem
                            displayPickStatus(checkedItem.getField(clsFieldDefine.yn_flag))
                            if(a_type == "UPDATE"){
                                printCheckedRow(checkedItem)
                            }
                        }
                        procedure_call("INFO")
                    }
                }
            }//end-switch
        } catch (ex: JSONException) {
            prgDlg!!.dismiss()
            clsCommon.setCustomToast(this, ex.message)
            Log.d("Json", ex.message)
            clsCommon.GetEditFocus(this, R.id.ed_loc_scan)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_CANCELED -> {
            }
            Activity.RESULT_FIRST_USER -> if (data?.getStringExtra("logout").equals("true"))
                clsCommon.LogoutProgram(this)
            else
                clsCommon.BackHomeProgram(this)
            Activity.RESULT_OK -> {
            }
            else -> {
            }
        }
    }

    override fun onClick(view: View) {
        val viewId = view.id
        when (viewId) {
            R.id.bt_find -> {
                imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                prgDlg = ProgressDialog.show(this@PickingActivity, clsGlobalDefine.FIND_TITLE, clsGlobalDefine.MSG_WAITING, true, true)
                procedure_call("INFO")
            }
            R.id.bt_reg -> {
                imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                prgDlg = ProgressDialog.show(this@PickingActivity, clsGlobalDefine.FIND_TITLE, clsGlobalDefine.MSG_WAITING, true, true)
                procedure_call("INSERT")
            }
            R.id.bt_pick_done, R.id.bt_qty_hold, R.id.bt_status_hold ->
                if (adapter.getCount() > 0) {
                    val n = listview.checkedItemPosition
                    if (n < 0) {
                        clsCommon.setCustomToast(this, "처리할 대상이 없습니다.")
                        return
                    }

                    imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                    if(viewId == R.id.bt_pick_done) procedure_call("UPDATE")
                    else if (viewId == R.id.bt_qty_hold) procedure_call("HOLD_QTY")
                    else procedure_call("HOLD_STATUS")
                }
            R.id.btn_home -> {
                imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                clsCommon.BackHomeProgram(this)
            }
            R.id.bt_back -> {
                imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                clsCommon.BackProgram(this, Activity.RESULT_CANCELED)
            }

            R.id.btn_logout -> {
                imm!!.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                clsCommon.BackProgram(this, Activity.RESULT_CANCELED)
            }
            R.id.bt_print // 프린트
            -> {
                val nChecked = listview.checkedItemPosition
                if (nChecked > -1) {
                    val checkedItem = listview.getItemAtPosition(nChecked) as ListViewItem
                    printCheckedRow(checkedItem)
                } else {
                    clsCommon.setCustomToast(this, "출력할 정보가 없습니다.")
                }

                clsCommon.GetEditFocus(this, R.id.ed_loc_scan)
                imm.hideSoftInputFromWindow(ed_loc_scan.windowToken, 0)
            }
        }
    }
}
