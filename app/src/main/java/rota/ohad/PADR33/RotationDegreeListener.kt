package rota.ohad.PADR33

interface RotationDegreeListener {
    fun onValueChange(value: Int)
    fun onValueTypeChange(valueType:Int)
    fun disableRotate()
}