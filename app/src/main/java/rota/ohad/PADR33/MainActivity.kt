package rota.ohad.PADR33

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.android.synthetic.main.activity_main.*
import rota.ohad.PADR33.RotaryKnobView.RotaryKnobListener
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() ,GestureDetector.OnGestureListener, OnTouchListener , RotaryKnobListener ,  AdapterView.OnItemSelectedListener{
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mmSocket: BluetoothSocket? = null
    private var mmDevice: BluetoothDevice? = null
    private var mmOutputStream: OutputStream? = null
    private var mmInputStream: InputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private  var serials = IntArray(24){0}
    private var sendSerials = IntArray(23){0}
    private var degreeListener : RotationDegreeListener ?= null
    private var mDetector: GestureDetector? = null
    private lateinit var pairedDevices: Set<BluetoothDevice>
    private val btDevicesNames = ArrayList<String>()
    private var knobState = 0
    private var viewId: Int? = null
    private var menuId: Int? = null
    private var shortClick = false
    @Volatile
    private var stopWorker = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mDetector = GestureDetector(this, this)
        buttonina.setOnTouchListener(this)
        buttoninb.setOnTouchListener(this)
        buttoninc.setOnTouchListener(this)
        buttonouta.setOnTouchListener(this)
        buttonoutb.setOnTouchListener(this)
        buttonoutc.setOnTouchListener(this)
        buttonmono.setOnTouchListener(this)
        buttondim.setOnTouchListener(this)
        buttonmute.setOnTouchListener(this)
        tkboffstate.setOnTouchListener(this)
        tkbdimstate.setOnTouchListener(this)
        tkbmutestate.setOnTouchListener(this)
        dimsetupmenutest.setOnTouchListener(this)
        tkbmenutext.setOnTouchListener(this)
        exitmenutext.setOnTouchListener(this)
        buttontbm.setOnTouchListener(OnTouchListener { _, event ->
            clickSerial(9,event)
            true
        })


        knob.listener = this
        degreeListener = knob
//        var tempArrays  = ArrayList<String>()
//        tempArrays.add("1")
//        tempArrays.add("1")
//        tempArrays.add("1")
//        val btDeviceNamesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tempArrays)
//        btDeviceNamesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        bluetooth_menu.adapter = btDeviceNamesAdapter
//        bluetooth_menu.onItemSelectedListener = this
        setSerials("<0,0,0,0,0,0,0,0,1,0,0,-127,0,1,0,0,80,0,0,1,0,1,0,20,>")
        findBT()
        if (mmDevice!==null) openBT()

        showSerial()
    }
    override fun onDestroy() {
        super.onDestroy()
        closeBT()
    }

    private fun findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            Toast.makeText(this,"No bluetooth adapter available", Toast.LENGTH_SHORT).show()
            return
        }
        if (mBluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 0)
        }

        pairedDevices = mBluetoothAdapter!!.bondedDevices

        Toast.makeText(this,"Select the Bluetooth Device", Toast.LENGTH_SHORT).show()
        if (!this::pairedDevices.isInitialized) return
        pairedDevices.forEach { device -> mmDevice=device }
//        val btDeviceNamesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, btDevicesNames)
//        btDeviceNamesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        bluetooth_menu.adapter = btDeviceNamesAdapter
//        bluetooth_menu.onItemSelectedListener = this
//        bluetooth_menu.visibility=View.VISIBLE

    }
    @Throws(IOException::class)
    fun openBT() {
        val uuid: UUID =
                UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID
        mmSocket = mmDevice!!.createInsecureRfcommSocketToServiceRecord(uuid)
        mmSocket!!.connect()
        mmOutputStream = mmSocket!!.outputStream
        mmInputStream = mmSocket!!.inputStream
        beginListenForData()
        Toast.makeText(this,mmDevice!!.name.toString()+" Connected", Toast.LENGTH_SHORT).show()
    }
    @Throws(IOException::class)
    fun sendData() {
        var data = ""
        for(i in 0..14){
            data += if (i==22){
                sendSerials[i].toString()
            }else {
                sendSerials[i].toString() + ","
            }
        }
        data = "<$data>"
        Log.e("outgoing serials", data.toString())
        mmOutputStream?.write(data.toByteArray())
    }
    @Throws(IOException::class)
    fun closeBT() {
        stopWorker = true;
        mmOutputStream!!.close();
        mmInputStream!!.close();
        mmSocket!!.close();
    }
    private fun beginListenForData() {
        val handler = Handler()
        val delimiter: Byte = 10 //This is the ASCII code for a newline character
        stopWorker = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)
        workerThread = Thread(Runnable {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable = mmInputStream!!.available()
                    if (bytesAvailable > 0) {
                        val packetBytes = ByteArray(bytesAvailable)
                        mmInputStream!!.read(packetBytes)
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            if (b == delimiter) {
                                val encodedBytes =
                                        ByteArray(readBufferPosition)
                                System.arraycopy(
                                        readBuffer,
                                        0,
                                        encodedBytes,
                                        0,
                                        encodedBytes.size
                                )
                                val characterSet =
                                        Charset.forName("US-ASCII")
                                val data = encodedBytes.toString(characterSet)
                                readBufferPosition = 0
                                handler.post(Runnable {
                                    setSerials(data)
                                    showSerial()
//                                    Toast.makeText(this,data, Toast.LENGTH_SHORT).show()
                                })
                            } else {
                                readBuffer[readBufferPosition++] = b
                            }
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
        })
        workerThread!!.start()
    }

    private fun setSerials(data: String){
        val regexPattern = "<([0,1],){10}((-)?(\\d){1,3},){3}([0,1],){3}((\\d){1,3},)([0,1],){3}([0,1,2,3],)([0,1],)([0,1,2,3],)((\\d){1,2},)>"
        val isMatch: Boolean = Pattern.matches(regexPattern, data)
        if (!isMatch) {
            Toast.makeText(this, "Wrong Serial Data Type", Toast.LENGTH_SHORT).show()
            return
        }
        val list = data.removeSurrounding("<", ",>").split(",")
        if(list[10].toInt()<-127) {
            Toast.makeText(this, "Wrong Serial Data Type", Toast.LENGTH_SHORT).show()
            return
        }
        serials = IntArray(list.size)
        for (i in list.indices) {
            serials[i] = list[i].toInt()
        }
    }
    private fun showSerial(){
        ledsHandle()
        lcdView()
        knobView()
    }
    private fun ledsHandle(){
        var ledColor : Int = if(serials[0]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledina.setBackgroundColor(ledColor)
        ledColor = if(serials[1]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledinb.setBackgroundColor(ledColor)
        ledColor = if(serials[2]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledinc.setBackgroundColor(ledColor)
        ledColor = if(serials[3]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledouta.setBackgroundColor(ledColor)
        ledColor = if(serials[4]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledoutb.setBackgroundColor(ledColor)
        ledColor = if(serials[5]==1) {ContextCompat.getColor(this, R.color.ledOrange)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledoutc.setBackgroundColor(ledColor)
        ledColor = if(serials[6]==1) {ContextCompat.getColor(this, R.color.ledWhite)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledmono.setBackgroundColor(ledColor)
        ledColor = if(serials[7]==1) {ContextCompat.getColor(this, R.color.ledBlue)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        leddim.setBackgroundColor(ledColor)
        ledColor = if(serials[8]==1) {ContextCompat.getColor(this, R.color.ledPink)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledmute.setBackgroundColor(ledColor)
        ledColor = if(serials[9]==1) {ContextCompat.getColor(this, R.color.ledGreen)}else{
            ContextCompat.getColor(this, R.color.ledBlack)
        }
        ledtb.setBackgroundColor(ledColor)

    }
    private fun lcdView(){
        var textInput =""
        if(serials[0]==1) textInput += 'A'
        if(serials[1]==1) textInput += 'B'
        if(serials[2]==1) textInput += 'C'
        inputstest.text = textInput
        var textOutput =""
        if(serials[3]==1) textOutput += 'A'
        if(serials[4]==1) textOutput += 'B'
        if(serials[5]==1) textOutput += 'C'
        outputstest.text = textOutput
        textmono.visibility = if(serials[6]==1) View.VISIBLE else View.INVISIBLE
        textdim.visibility = if(serials[7]==1) View.VISIBLE else View.INVISIBLE
        mutetext.visibility = if(serials[8]==1) View.VISIBLE else View.INVISIBLE
        muteltext.visibility = if(serials[13]==1) View.VISIBLE else View.INVISIBLE
        mutertext.visibility = if(serials[14]==1) View.VISIBLE else View.INVISIBLE
        dbValueText.visibility = if(serials[8]==1 || serials[13] == 1 || serials[14] == 1) View.INVISIBLE else View.VISIBLE
        lastdbValue.visibility = if(serials[8]==1 || serials[13] == 1 || serials[14] == 1) View.INVISIBLE else View.VISIBLE

        val animBlinkingEffect: Animation = AlphaAnimation(0.0f, 1.0f)
        animBlinkingEffect.duration = 300 //You can manage the blinking time with this parameter
        animBlinkingEffect.startOffset = 20
        animBlinkingEffect.repeatMode = Animation.REVERSE
        animBlinkingEffect.repeatCount = 4
        if(serials[17]==1) textdim.startAnimation(animBlinkingEffect)
        if(serials[18]==1) mutetext.startAnimation(animBlinkingEffect)
        if(serials[19]==1) {
            knobState = if(serials[15]==1) {
                dimMenuShow()
                2
            }else{ if (serials[21]==1) {
                tbkMenuShow()
                4
            } else{
                mainMenuShow()
                3
            }
            }
        }else{
            knobState =  if( serials[13] == 1 || serials[14] == 1) {
                muteMenuShow()
                1
            } else {

                if(serials[8]==1) {
                    muteMenuShow()
                    5
                }else {
                    defaultMenuShow()
                    0
                }
            }
        }
        mainMenuSelected(serials[20])
        tbkMenuSelected(serials[22])
        textdbvalue.text = serials[10].toString()

        if (serials[11] == -127) lastdbtest.text  = "-∞" else  lastdbtest.text = serials[11].toString()
        lastdimdb.text = serials[12].toString()
        setupdimdbtest.text = serials[16].toString()

    }
    private fun knobView(){
        degreeListener?.onValueTypeChange(knobState)
        when(knobState){
            0->{
                degreeListener?.onValueChange(serials[10])
            }
            1->{
                degreeListener?.onValueChange(serials[23])
            }
            2->{
                degreeListener?.onValueChange(serials[16])
            }
            3->{
                degreeListener?.onValueChange(serials[20])
            }
            4->{
                degreeListener?.onValueChange(serials[22])
            }
            5->{
                degreeListener?.disableRotate()
            }
        }
    }

    private fun muteMenuShow(){
        muteMenu.visibility=View.VISIBLE
        mainMenu.visibility=View.INVISIBLE
        defaultMenu.visibility=View.INVISIBLE
        dimSetupMenu.visibility=View.INVISIBLE
        tbkSetupMenu.visibility=View.INVISIBLE
    }
    private fun mainMenuShow(){
        muteMenu.visibility=View.INVISIBLE
        mainMenu.visibility=View.VISIBLE
        defaultMenu.visibility=View.INVISIBLE
        dimSetupMenu.visibility=View.INVISIBLE
        tbkSetupMenu.visibility=View.INVISIBLE
    }
    private fun dimMenuShow(){
        muteMenu.visibility=View.INVISIBLE
        mainMenu.visibility=View.INVISIBLE
        defaultMenu.visibility=View.INVISIBLE
        dimSetupMenu.visibility=View.VISIBLE
        tbkSetupMenu.visibility=View.INVISIBLE
    }
    private fun tbkMenuShow(){
        muteMenu.visibility=View.INVISIBLE
        mainMenu.visibility=View.INVISIBLE
        defaultMenu.visibility=View.INVISIBLE
        dimSetupMenu.visibility=View.INVISIBLE
        tbkSetupMenu.visibility=View.VISIBLE
    }
    private fun defaultMenuShow(){
        muteMenu.visibility=View.INVISIBLE
        mainMenu.visibility=View.INVISIBLE
        defaultMenu.visibility=View.VISIBLE
        dimSetupMenu.visibility=View.INVISIBLE
        tbkSetupMenu.visibility=View.INVISIBLE
    }
    private fun mainMenuSelected(position: Int){
        when(position){
            0->{
                dimsetupmenutest.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                exitmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            1-> {
                dimsetupmenutest.background = ContextCompat.getDrawable(this, R.drawable.menu_border)
                tkbmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                exitmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            2->{
                dimsetupmenutest.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_border)
                exitmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            3->{
                dimsetupmenutest.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                exitmenutext.background = ContextCompat.getDrawable(this,R.drawable.menu_border)
            }
        }
    }
    private fun tbkMenuSelected(position: Int){
        when(position){
            0-> {
                tkboffstate.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbdimstate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                tkbmutestate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            1-> {
                tkboffstate.background = ContextCompat.getDrawable(this, R.drawable.menu_border)
                tkbdimstate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                tkbmutestate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            2->{
                tkboffstate.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbdimstate.background = ContextCompat.getDrawable(this,R.drawable.menu_border)
                tkbmutestate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
            }
            3->{
                tkboffstate.background = ContextCompat.getDrawable(this, R.drawable.menu_no_border)
                tkbdimstate.background = ContextCompat.getDrawable(this,R.drawable.menu_no_border)
                tkbmutestate.background = ContextCompat.getDrawable(this,R.drawable.menu_border)
            }
        }

    }

    fun buttontbl(view: View) {
        Toast.makeText(this,"TKB", Toast.LENGTH_SHORT).show()
        if (sendSerials[9]==0) {
            sendSerials[9]=1
        }else{
            sendSerials[9]=0
        }
        sendData()
    }

    private fun clickSerial(index:Int, event: MotionEvent?){
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> if (!shortClick) {
                shortClick = true
                sendSerials[index] = 1
                sendData()
            }
            MotionEvent.ACTION_UP -> {
                sendSerials[index] = 0
                sendData()
                shortClick = false
            }
        }
    }

    override fun onRotate(value: Int) {
        textdbvalue.text = value.toString()
        if (value == -127){
            textdbvalue.text  = "-∞";
            textdb.visibility=View.INVISIBLE
        }else{
            textdb.visibility=View.VISIBLE
        }

        setupdimdbtest.text = value.toString()
        mainMenuSelected(value)
        tbkMenuSelected(value)
        when(knobState){
            0->{
                sendSerials[10] = value
            }
            1->{
                sendSerials[11] = value
            }
            2->{
                sendSerials[12] = value
            }
            3->{
                sendSerials[13] = value
            }
            4->{
                sendSerials[14] = value
            }
        }
        sendData()
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        when (view) {
            buttonina -> { viewId = 0 }
            buttoninb -> { viewId = 1 }
            buttoninc -> { viewId = 2 }
            buttonouta -> { viewId = 3 }
            buttonoutb -> { viewId = 4 }
            buttonoutc -> { viewId = 5 }
            buttonmono -> { viewId = 6 }
            buttondim -> { viewId = 7 }
            buttonmute -> { viewId = 8 }
            dimsetupmenutest -> {
                viewId = 13
                menuId =1
            }
            tkbmenutext -> {
                viewId = 13
                menuId =2
            }
            exitmenutext -> {
                viewId = 13
                menuId =3
            }
            tkboffstate -> {
                viewId = 14
                menuId =1
            }
            tkbdimstate -> {
                viewId = 14
                menuId =2
            }
            tkbmutestate -> {
                viewId = 14
                menuId = 3
            }

        }

        return mDetector!!.onTouchEvent(event)
    }

    override fun onShowPress(p0: MotionEvent?) {
    }
    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        if (viewId!! <= 8) {
            sendSerials[viewId!!] = 1
            sendData()
            sendSerials[viewId!!] = 0
            sendData()
        }else{
            sendSerials[viewId!!] = menuId!!
            sendData()
        }
        return true
    }
    override fun onDown(p0: MotionEvent?): Boolean {
        return true
    }
    override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return true
    }
    override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return true
    }
    override fun onLongPress(p0: MotionEvent?) {
        if(viewId!! !in 3..6) {
            sendSerials[viewId!!] = 2
            sendData()
            sendSerials[viewId!!] = 0
            sendData()
        }
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {}
    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
//        Log.e("spinner",position.toString())
//        if (!this::pairedDevices.isInitialized) return
//        mmDevice = pairedDevices.elementAt(position)
//        if (mmDevice!==null) openBT()
    }
}









