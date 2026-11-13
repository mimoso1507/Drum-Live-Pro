package com.drummatch.deluxe.model

/** Icon identifiers. Every instrument icon is drawn by [com.drummatch.deluxe.ui.IconPainter]. */
enum class Icon(val label: String) {
    KICK("Bombo"), SNARE("Caja"), SNARE_ELEC("Caja electrónica"), SIDE_STICK("Aro / Side stick"),
    CLAP("Palmas"), SNAP("Chasquido"), HIHAT_CLOSED("Hi-hat cerrado"), HIHAT_OPEN("Hi-hat abierto"),
    HIHAT_PEDAL("Hi-hat pedal"), TOM_HIGH("Tom agudo"), TOM_MID("Tom medio"), TOM_LOW("Tom grave"),
    TOM_FLOOR("Tom de piso"), CRASH("Crash"), RIDE("Ride"), RIDE_BELL("Campana del ride"),
    CHINA("China"), SPLASH("Splash"), STACK("Stack"), TAMBOURINE("Pandereta"), COWBELL("Cencerro"),
    VIBRASLAP("Vibraslap"), BONGO("Bongó"), CONGA("Conga"), TIMBALE("Timbal"), AGOGO("Agogó"),
    CABASA("Cabasa"), MARACAS("Maracas"), WHISTLE("Silbato"), GUIRO("Güiro"), CLAVES("Claves"),
    WOODBLOCK("Bloque de madera"), CUICA("Cuíca"), TRIANGLE("Triángulo"), SHAKER("Shaker"),
    JINGLE_BELL("Cascabeles"), BELL_TREE("Árbol de campanas"), CASTANETS("Castañuelas"),
    SURDO("Surdo"), HIGH_Q("High Q (sintético)"), SLAP("Slap"), SCRATCH("Scratch"), STICKS("Baquetas"),
    SQUARE_CLICK("Click cuadrado"), METRONOME("Metrónomo"), METRO_BELL("Campana metrónomo"),
    GONG("Gong"), TIMPANI("Timbales sinfónicos"), CAJON("Cajón"), DJEMBE("Djembé"), TABLA("Tabla"),
    CHIMES("Chimes"), DRUM_MACHINE("Caja de ritmos"), PAD_GENERIC("Pad"),
    // Extra icons for kits
    DRUMKIT("Batería"), STAR("Estrella"), BOLT("Rayo"), HEART("Corazón"), FIRE("Fuego"), NOTE("Nota"),
    HEADPHONES("Audífonos"), MIC("Micrófono"), SPEAKER("Parlante"), VINYL("Vinilo"), GUITAR("Guitarra"),
    PIANO("Piano"), WAVE("Onda"), CROWN("Corona"), SKULL("Calavera"), ROCKET("Cohete"), MOON("Luna"),
    SUN("Sol"), DIAMOND("Diamante"), ROBOT("Robot"), GLOBE("Mundo"), LEAF("Hoja"), SPARKLE("Brillo"),
    EQ("Ecualizador"), PALM("Palmera"), MASK("Máscara"), FLAME_SKULL("Metal"), CLOUD("Nube"),
    TRUMPET("Trompeta"), SAX("Saxofón"), CROSS_STICKS("Baquetas cruzadas"), CIRCLE_WAVE("Pulso");

    companion object {
        val instrumentIcons: List<Icon> get() = values().filter { it.ordinal <= PAD_GENERIC.ordinal }
        val all: List<Icon> get() = values().toList()
        fun byName(n: String?): Icon? = values().firstOrNull { it.name == n }
    }
}

/** Musical role used for AutoPlay pattern generation and for the virtual kit layout. */
enum class Role { KICK, SNARE, HAT_CLOSED, HAT_OPEN, HAT_PEDAL, RIDE, CRASH, TOM_HIGH, TOM_MID, TOM_LOW, TOM_FLOOR, PERC_HAND, PERC_SHAKE, PERC_BELL, PERC_WOOD, FX }

data class Instrument(val note: Int, val name: String, val icon: Icon, val role: Role)

object Instruments {
    private val NOTE_NAMES = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    fun noteName(n: Int): String = NOTE_NAMES[n % 12] + (n / 12 - 1)

    private val gm = listOf(
        Instrument(27, "High Q", Icon.HIGH_Q, Role.FX),
        Instrument(28, "Slap", Icon.SLAP, Role.PERC_HAND),
        Instrument(29, "Scratch empuje", Icon.SCRATCH, Role.FX),
        Instrument(30, "Scratch tirón", Icon.SCRATCH, Role.FX),
        Instrument(31, "Baquetas", Icon.STICKS, Role.PERC_WOOD),
        Instrument(32, "Click cuadrado", Icon.SQUARE_CLICK, Role.PERC_WOOD),
        Instrument(33, "Click metrónomo", Icon.METRONOME, Role.PERC_WOOD),
        Instrument(34, "Campana metrónomo", Icon.METRO_BELL, Role.PERC_BELL),
        Instrument(35, "Bombo acústico", Icon.KICK, Role.KICK),
        Instrument(36, "Bombo", Icon.KICK, Role.KICK),
        Instrument(37, "Aro (side stick)", Icon.SIDE_STICK, Role.SNARE),
        Instrument(38, "Caja acústica", Icon.SNARE, Role.SNARE),
        Instrument(39, "Palmas", Icon.CLAP, Role.SNARE),
        Instrument(40, "Caja eléctrica", Icon.SNARE_ELEC, Role.SNARE),
        Instrument(41, "Tom de piso grave", Icon.TOM_FLOOR, Role.TOM_FLOOR),
        Instrument(42, "Hi-hat cerrado", Icon.HIHAT_CLOSED, Role.HAT_CLOSED),
        Instrument(43, "Tom de piso agudo", Icon.TOM_FLOOR, Role.TOM_FLOOR),
        Instrument(44, "Hi-hat pedal", Icon.HIHAT_PEDAL, Role.HAT_PEDAL),
        Instrument(45, "Tom grave", Icon.TOM_LOW, Role.TOM_LOW),
        Instrument(46, "Hi-hat abierto", Icon.HIHAT_OPEN, Role.HAT_OPEN),
        Instrument(47, "Tom medio grave", Icon.TOM_MID, Role.TOM_MID),
        Instrument(48, "Tom medio agudo", Icon.TOM_MID, Role.TOM_MID),
        Instrument(49, "Crash 1", Icon.CRASH, Role.CRASH),
        Instrument(50, "Tom agudo", Icon.TOM_HIGH, Role.TOM_HIGH),
        Instrument(51, "Ride 1", Icon.RIDE, Role.RIDE),
        Instrument(52, "China", Icon.CHINA, Role.CRASH),
        Instrument(53, "Campana del ride", Icon.RIDE_BELL, Role.RIDE),
        Instrument(54, "Pandereta", Icon.TAMBOURINE, Role.PERC_SHAKE),
        Instrument(55, "Splash", Icon.SPLASH, Role.CRASH),
        Instrument(56, "Cencerro", Icon.COWBELL, Role.PERC_BELL),
        Instrument(57, "Crash 2", Icon.CRASH, Role.CRASH),
        Instrument(58, "Vibraslap", Icon.VIBRASLAP, Role.FX),
        Instrument(59, "Ride 2", Icon.RIDE, Role.RIDE),
        Instrument(60, "Bongó agudo", Icon.BONGO, Role.PERC_HAND),
        Instrument(61, "Bongó grave", Icon.BONGO, Role.PERC_HAND),
        Instrument(62, "Conga apagada", Icon.CONGA, Role.PERC_HAND),
        Instrument(63, "Conga abierta", Icon.CONGA, Role.PERC_HAND),
        Instrument(64, "Conga grave", Icon.CONGA, Role.PERC_HAND),
        Instrument(65, "Timbal agudo", Icon.TIMBALE, Role.PERC_HAND),
        Instrument(66, "Timbal grave", Icon.TIMBALE, Role.PERC_HAND),
        Instrument(67, "Agogó agudo", Icon.AGOGO, Role.PERC_BELL),
        Instrument(68, "Agogó grave", Icon.AGOGO, Role.PERC_BELL),
        Instrument(69, "Cabasa", Icon.CABASA, Role.PERC_SHAKE),
        Instrument(70, "Maracas", Icon.MARACAS, Role.PERC_SHAKE),
        Instrument(71, "Silbato corto", Icon.WHISTLE, Role.FX),
        Instrument(72, "Silbato largo", Icon.WHISTLE, Role.FX),
        Instrument(73, "Güiro corto", Icon.GUIRO, Role.PERC_SHAKE),
        Instrument(74, "Güiro largo", Icon.GUIRO, Role.PERC_SHAKE),
        Instrument(75, "Claves", Icon.CLAVES, Role.PERC_WOOD),
        Instrument(76, "Bloque agudo", Icon.WOODBLOCK, Role.PERC_WOOD),
        Instrument(77, "Bloque grave", Icon.WOODBLOCK, Role.PERC_WOOD),
        Instrument(78, "Cuíca apagada", Icon.CUICA, Role.FX),
        Instrument(79, "Cuíca abierta", Icon.CUICA, Role.FX),
        Instrument(80, "Triángulo apagado", Icon.TRIANGLE, Role.PERC_BELL),
        Instrument(81, "Triángulo abierto", Icon.TRIANGLE, Role.PERC_BELL),
        Instrument(82, "Shaker", Icon.SHAKER, Role.PERC_SHAKE),
        Instrument(83, "Cascabeles", Icon.JINGLE_BELL, Role.PERC_SHAKE),
        Instrument(84, "Árbol de campanas", Icon.BELL_TREE, Role.FX),
        Instrument(85, "Castañuelas", Icon.CASTANETS, Role.PERC_WOOD),
        Instrument(86, "Surdo apagado", Icon.SURDO, Role.TOM_FLOOR),
        Instrument(87, "Surdo abierto", Icon.SURDO, Role.TOM_FLOOR),
    ).associateBy { it.note }

    fun forNote(note: Int): Instrument = gm[note] ?: Instrument(note, "Nota $note", Icon.PAD_GENERIC, guessRole(note))

    private fun guessRole(note: Int): Role = when {
        note < 27 -> Role.FX
        note > 87 -> Role.FX
        else -> Role.PERC_HAND
    }

    fun label(note: Int): String {
        val i = gm[note]
        return if (i != null) "$note · ${i.name} (${noteName(note)})" else "$note · ${noteName(note)}"
    }

    fun roleForIcon(icon: Icon, fallback: Role): Role = when (icon) {
        Icon.KICK -> Role.KICK
        Icon.SNARE, Icon.SNARE_ELEC, Icon.SIDE_STICK, Icon.CLAP, Icon.SNAP -> Role.SNARE
        Icon.HIHAT_CLOSED -> Role.HAT_CLOSED
        Icon.HIHAT_OPEN -> Role.HAT_OPEN
        Icon.HIHAT_PEDAL -> Role.HAT_PEDAL
        Icon.RIDE, Icon.RIDE_BELL -> Role.RIDE
        Icon.CRASH, Icon.CHINA, Icon.SPLASH, Icon.STACK, Icon.GONG -> Role.CRASH
        Icon.TOM_HIGH -> Role.TOM_HIGH
        Icon.TOM_MID -> Role.TOM_MID
        Icon.TOM_LOW -> Role.TOM_LOW
        Icon.TOM_FLOOR, Icon.SURDO, Icon.TIMPANI -> Role.TOM_FLOOR
        Icon.BONGO, Icon.CONGA, Icon.TIMBALE, Icon.CAJON, Icon.DJEMBE, Icon.TABLA, Icon.SLAP -> Role.PERC_HAND
        Icon.TAMBOURINE, Icon.CABASA, Icon.MARACAS, Icon.SHAKER, Icon.GUIRO, Icon.JINGLE_BELL -> Role.PERC_SHAKE
        Icon.COWBELL, Icon.AGOGO, Icon.TRIANGLE, Icon.METRO_BELL, Icon.CHIMES -> Role.PERC_BELL
        Icon.CLAVES, Icon.WOODBLOCK, Icon.CASTANETS, Icon.STICKS, Icon.SQUARE_CLICK, Icon.METRONOME -> Role.PERC_WOOD
        else -> fallback
    }

    /** Default note order used when pads are auto-assigned (rows of 4). */
    val defaultLayout = intArrayOf(
        49, 51, 46, 42,   // crash, ride, open hh, closed hh
        50, 47, 43, 44,   // toms + pedal hh
        36, 38, 39, 56,   // kick, snare, clap, cowbell
        37, 55, 52, 54,   // side stick, splash, china, tambourine
        60, 61, 63, 64,   // bongos, congas
        65, 66, 70, 75,   // timbales, maracas, claves
        76, 81, 69, 73,   // woodblock, triangle, cabasa, guiro
        67, 68, 82, 83,   // agogo, shaker, jingle bell
    )
}
