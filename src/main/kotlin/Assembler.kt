import java.io.File

val variables: MutableMap<String, Int> = mutableMapOf()

fun main(args: Array<String>) {
    File("asm/").walkTopDown().filter { it.extension == "asm" }.forEach {
        variables.clear()
        val generatedCode: String = assemble(it)
        val out: File = File("asm/${it.nameWithoutExtension}.hack")
        out.writeText(generatedCode)
        val refText = File("asm/${it.nameWithoutExtension}_reference.hack").readLines()
        val createdText = out.readLines()
        val cleanAsm = clean(it)
        if (refText.size == createdText.size) {
            refText.forEachIndexed { index, line ->
                if (refText[index] != createdText[index]) {
                    val lines: MutableList<String> = mutableListOf<String>()
                    for (i in -3..3) {
                        lines.add(cleanAsm[index + i])
                    }
                    throw Exception(
                        "Reference and generated code different at line ${index}\nLine in ASM: ${cleanAsm[index]} \n${lines.joinToString(
                            "\n"
                        )}"
                    )
                }
            }
        } else {
            throw Exception("Length of reference and generated code not the same for ${it.nameWithoutExtension}")
        }
    }

}

fun clean(f: File): List<String> {
    val commentRegex = "\\/\\/.*\$".toRegex()
    val labelRegex = "\\(.*\\)".toRegex()


    return f.readLines().map { it.replace(commentRegex, "") }.map { it.replace("\\s".toRegex(), "") }
        .filter { !it.equals("") }.filter { it.matches(labelRegex) != null }

}

fun assemble(f: File): String {
    val commentRegex = "\\/\\/.*\$".toRegex()

    val code: List<String> = f.readLines().map { it.replace(commentRegex, "") }.map { it.replace("\\s".toRegex(), "") }
        .filter { !it.equals("") }
    val (codeProcessedLabels, labels) = labelize(code)
    val codeProcessedInstructions = processInstructions(codeProcessedLabels, labels)
    return codeProcessedInstructions.joinToString("\n")
}


/**
 * Labelize
 * @param code A list containing the string value of each line of a Hack ASM file
 * @return A list of strings with any label instructions and a label <-> line number mapping
 */
fun labelize(code: List<String>): Pair<List<String>, Map<String, Int>> {

    val labelRegex = "\\(.*\\)".toRegex()
    val labels: MutableMap<String, Int> = mutableMapOf()

    val code_1: MutableList<String> = mutableListOf()
    var counter = 0
    for (i in code) {
        val match = labelRegex.find(i)
        if (!(match == null)) {
            labels.put(match?.groupValues?.first().slice(1..i.length - 2), counter)
            continue
        }
        code_1.add(i)
        counter++
    }
    return Pair(code_1, labels)
}

fun processInstructions(code: List<String>, labels: Map<String, Int>): List<String> {
    val code_1: MutableList<String> = mutableListOf()

    for (i in code) {
        var instruction = ""
        if (i.get(0) == "@".single()) {
            // A Instruction
            val num = (symbol(i.slice(1..i.length - 1), labels).toInt() and 0x7fff).toString(2)
            instruction = "0${num.padStart(15, "0".single())}"
        } else {
            // C Instruction
            val (dest, cmp, jmp) = splitCInstruction(i.toLowerCase())

            val destA = dest.contains("a")
            val destD = dest.contains("d")
            val destM = dest.contains("m")
            val destBinary = listOf<Boolean>(destA, destD, destM).map { if (it) "1" else "0" }.joinToString("")

            val cmpBinary = cmpArray()[cmp]

            val jmpEq = jmp == "jeq" || jmp == "jge" || jmp == "jle" || jmp == "jmp"
            val jmpLt = jmp == "jlt" || jmp == "jne" || jmp == "jle" || jmp == "jmp"
            val jmpGt = jmp == "jgt" || jmp == "jge" || jmp == "jne" || jmp == "jmp"
            val jmpBinary = listOf<Boolean>(jmpLt, jmpEq, jmpGt).map { if (it) "1" else "0" }.joinToString("")
            instruction = "111${cmpBinary}${destBinary}${jmpBinary}"
        }
        code_1.add(instruction)
    }
    return code_1
}

fun splitCInstruction(instr: String): List<String> {
    var dest = ""
    var cmp = ""
    var jmp = ""

    val splitAtDest = instr.split("=")
    if (splitAtDest.size > 1) {
        dest = splitAtDest.first()
    }

    cmp = instr.split("=").last().split(";").first()


    val splitAtJmp = instr.split(";")
    if (splitAtJmp.size > 1) {
        jmp = splitAtJmp.last()
    }

    return listOf(dest, cmp, jmp)
}

fun symbol(sym: String, labels: Map<String, Int>): String {
    if ((sym.toIntOrNull() == null)) {
        if (sym.get(0) == "R".single() && sym.slice(1..sym.length - 1).toIntOrNull() != null) {
            return "${sym.slice(1..sym.length - 1)}"
        }
        val symbol = when (sym) {
            "SP" -> "0"
            "LCL" -> "1"
            "ARG" -> "2"
            "THIS" -> "3"
            "THAT" -> "4"
            "SCREEN" -> "16384"
            "KBD" -> "24576"
            else -> null
        }
        if (symbol != null) return symbol
        if (labels.get(sym) != null) {
            return labels.get(sym)!!.toString()
        } else {
            if (sym !in variables) variables.put(sym, 16 + variables.size)
            return "${variables.get(sym)}"
        }

    }
    return sym.toIntOrNull().toString()
}

fun cmpArray(): Map<String, String> {
    // i hate this
    val cmp: MutableMap<String, String> = mutableMapOf()

    // first char is A bit, determines a/m

    cmp["0"] = "0101010"
    cmp["1"] = "0111111"
    cmp["-1"] = "0111010"
    cmp["d"] = "0001100"
    cmp["a"] = "0110000"
    cmp["m"] = "1110000"
    cmp["!d"] = "0001101"
    cmp["!a"] = "0110001"
    cmp["!m"] = "1110001"
    cmp["-d"] = "0001111"
    cmp["-a"] = "0110011"
    cmp["-m"] = "1110011"
    cmp["d+1"] = "0011111"
    cmp["a+1"] = "0110111"
    cmp["m+1"] = "1110111"
    cmp["d-1"] = "0001110"
    cmp["a-1"] = "0110010"
    cmp["m-1"] = "1110010"
    cmp["d+a"] = "0000010"
    cmp["d+m"] = "1000010"
    cmp["d-a"] = "0010011"
    cmp["d-m"] = "1010011"
    cmp["a-d"] = "0000111"
    cmp["m-d"] = "1000111"
    cmp["d&a"] = "0000000"
    cmp["d&m"] = "1000000"
    cmp["d|a"] = "0010101"
    cmp["d|m"] = "1010101"

    return cmp
}