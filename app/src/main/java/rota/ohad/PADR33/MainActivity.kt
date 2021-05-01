package rota.ohad.PADR33

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import rota.ohad.PADR33.RotaryKnobView.RotaryKnobListener
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class MainActivity : AppCompatActivity() ,GestureDetector.OnGestureListener, View.OnTouchListener , RotaryKnobListener{
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mmSocket: BluetoothSocket? = null
    private var mmDevice: BluetoothDevice? = null
    private var mmOutputStream: OutputStream? = null
    private var mmInputStream: InputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private var serials = IntArray(23){0}
    private var sendSerials = IntArray(23){0}

    private var degreeListener : RotationDegreeListener ?= null
    private var mDetector: GestureDetector? = null

    @Volatile
    private var stopWorker = false

    private fun findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        var deviceName: String?=null
        if (mBluetoothAdapter == null) {
            Toast.makeText(this,"No bluetooth adapter available", Toast.LENGTH_SHORT).show()
            return
        }
        if (mBluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 0)
        }
        val pairedDevices: Set<BluetoothDevice>? = mBluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
        }
        if (pairedDevices?.size!! > 0) {
            for (device in pairedDevices) {
                if (device.name == deviceName) {
                    mmDevice = device
                    Toast.makeText(this,device.name+"Found", Toast.LENGTH_SHORT).show()
                    break
                }
            }
        }
    }

    @Throws(IOException::class)
    fun openBT() {
        val uuid: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID
        mmSocket = mmDevice!!.createRfcommSocketToServiceRecord(uuid)
        mmSocket!!.connect()
        mmOutputStream = mmSocket!!.outputStream
        mmInputStream = mmSocket!!.inputStream
        beginListenForData()
        Toast.makeText(this,"Rota's MacBook Air Opened", Toast.LENGTH_SHORT).show()
    }

    @Throws(IOException::class)
    fun sendData() {
        var data = ""
        for(i in 0..10){
            data += if (i==22){
                sendSerials[i].toString()
            }else {
                sendSerials[i].toString() + ","
            }
        }
        data = "<$data>"
        Log.e("serial",data)

        mmOutputStream?.write(data.toByteArray())
    }
    @Throws(IOException::class)
    fun closeBT() {
        stopWorker = true;
        mmOutputStream!!.close();
        mmInputStream!!.close();
        mmSocket!!.close();
//    myLabel.setText("Bluetooth Closed");
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
        var regexPattern = "<([0,1],){10}((-)?(\\d){1,3},){3}([0,1],){10}>"
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
        for (i in list.indices) {
            serials[i] = list[i].toInt()
        }
    }

    private fun showSerial(){
        ledsHandle()
        lcdView()
        dbView()
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
        buttontbm.setOnTouchListener(OnTouchListener { v, event ->
            clickSerial(9,event)
            true
        })
        ledsHandle()
        knob.listener = this
        degreeListener = knob
        setSerials("<0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,0,0,0,>")
        findBT()
        if (mmDevice!==null) openBT()
        showSerial()
    }
    override fun onRotate(value: Int) {
        textdbvalue.text = value.toString()

        if (value == -127){
            textdbvalue.text  = "-∞";
            textdb.text ="";
        }else{
            textdb.text ="DB";
        }
        sendSerials[10]=value
        sendData()
    }

//    fun outputLedControl(position:Int){
//        for(val index in (0...2) )
//    }
    private var blinkCount = 0
    private val executor: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)

    private fun startScheduler() {
        executor.scheduleAtFixedRate({ //DO YOUR THINGS
            textdim.visibility =  if(textdim.visibility == View.VISIBLE) {View.INVISIBLE}else{View.VISIBLE}
            Log.e("schedule",blinkCount.toString())
            blinkCount++
        }, 10, 300, TimeUnit.MILLISECONDS)
    }


    private fun stopScheduler() {
        textdim.visibility = View.INVISIBLE
        blinkCount = 0
    }
    private var carousalTimer: Timer? = null
    private var visible = View.VISIBLE
    private fun startTimer() {
        carousalTimer = Timer() // At this line a new Thread will be created
        Log.e("schedule",carousalTimer.toString())

        carousalTimer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                visible =  if(visible == View.VISIBLE) {View.INVISIBLE} else {View.VISIBLE}
                Log.e("schedule",visible.toString())
                blinkCount++
                if(blinkCount>=8) { stopTimer() }
            }
        }, 0, 500) // delay
    }

    fun stopTimer() {
        carousalTimer!!.cancel()
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
        if(serials[6]==1) {
            textmono.visibility = View.VISIBLE
        }else{
            textmono.visibility = View.INVISIBLE
        }
        if(serials[7] ==1) {
            textdim.visibility = View.VISIBLE
        }else{
            textdim.visibility = View.INVISIBLE
        }
        if(serials[17]==1) {
//            startScheduler()
//            if(blinkCount>=6) {
//                stopScheduler()
//            }
            Log.e("schedule","start")
            startScheduler()
            textdim.visibility = visible
        }
        if(serials[18]==1) {
            mutetext.visibility = View.VISIBLE
        }else{
            mutetext.visibility = View.INVISIBLE
        }
        if(serials[8]==1) {
            mutetext.visibility = View.VISIBLE
            textdbvalue.visibility = View.INVISIBLE
            textdb.visibility = View.INVISIBLE
        }else{
            mutetext.visibility = View.INVISIBLE
            textdbvalue.visibility = View.VISIBLE
            textdb.visibility = View.VISIBLE
        }
        if(serials[13]==1) {
            muteltext.visibility = View.VISIBLE
        }else{
            muteltext.visibility = View.INVISIBLE
        }
        if(serials[14]==1) {
            mutertext.visibility = View.VISIBLE
        }else{
            mutertext.visibility = View.INVISIBLE
        }
        lastdbtest.text = serials[11].toString()
        lastdbtest.visibility =View.VISIBLE
        lastdimdb.visibility =View.VISIBLE
//         if(serials[11]) {
//             lastdbtest.visibility =View.VISIBLE
//        }else{
//             lastdbtest.visibility =View.INVISIBLE
//        }
        lastdimdb.text = serials[12].toString()
}

    private  fun dbView(){
        textdbvalue.text = serials[10].toString()
        Log.e("knob",knob.knobDrawable.toString())
        degreeListener?.onValueChange(serials[10])
    }

    //  Buttons actions


    fun buttontbl(view: View) {
        Toast.makeText(this,"TKB", Toast.LENGTH_SHORT).show()
        if (sendSerials[9]==0) {
            sendSerials[9]=1
        }else{
            sendSerials[9]=0
        }
        sendData()
    }

    override fun onRotateStop(value: Int) {
//        textdbvalue.text = value.toString()
//
//        if (value == -127){
//            textdbvalue.text  = "-∞";
//            textdb.text ="";
//        }else{
//            textdb.text ="DB";
//        }
//        serials[10]=value
//        Toast.makeText(this,"rotate stoped", Toast.LENGTH_SHORT).show()
//
//        sendData()
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
    private var viewId: Int? = null
    private var shortClick = false
    private var longClick = false
    private var eventStartTime : Long? =null
    private var eventEndTime : Long? =null
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
        }

        return mDetector!!.onTouchEvent(event)
    }

    override fun onShowPress(p0: MotionEvent?) {
    }

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        sendSerials[viewId!!]=1
        sendData()
        sendSerials[viewId!!]=0
        sendData()
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
        if(viewId!=9) {
            sendSerials[viewId!!] = 2
            sendData()
            sendSerials[viewId!!] = 0
            sendData()
        }else{
            sendSerials[viewId!!] = 1
            sendData()
            sendSerials[viewId!!] = 0
            sendData()
        }
    }

}








