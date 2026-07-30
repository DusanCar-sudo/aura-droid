package dev.aura.auradroid.data.session

/**
 * Names a conversation from what was said in it, in a word or two.
 *
 * Deliberately not a model call. Titling happens on the first message of every
 * conversation, including ones started with no network and ones the user
 * abandons immediately, and paying a round-trip and a fraction of a cent to
 * label something nobody may return to is the wrong trade. This runs in
 * microseconds, offline, for free.
 *
 * The scoring is a stand-in for "which word would a person have picked":
 * something repeated matters, something said first matters, something specific
 * (a file name, a library, a proper noun) matters more than a common verb. The
 * stop lists cover English and Serbian because this phone is dictated to in
 * both, and an untrimmed Serbian sentence otherwise titles itself "Da Li".
 */
object SessionNamer {

    /** What a conversation is called before anything has been said in it. */
    const val UNTITLED = "New Chat"

    /**
     * A title for [text], or [UNTITLED] when there is nothing to go on.
     *
     * [maxWords] is two by design: one word is often ambiguous ("build"), and
     * three stops fitting the session list on a phone.
     */
    fun titleFor(text: String, maxWords: Int = 2): String {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return UNTITLED

        val scores = mutableMapOf<String, Double>()
        val firstForm = mutableMapOf<String, String>()
        val firstIndex = mutableMapOf<String, Int>()
        val positions = mutableMapOf<String, MutableList<Int>>()

        tokens.forEachIndexed { index, token ->
            val key = token.lowercase()
            if (key in STOP_WORDS) return@forEachIndexed
            if (!isCandidate(token)) return@forEachIndexed

            positions.getOrPut(key) { mutableListOf() }.add(index)

            // Repetition is the strongest signal a word is the subject, but it
            // saturates: a word said six times is not three times as much the
            // topic as one said twice.
            val occurrence = scores.getOrDefault(key, 0.0)
            scores[key] = occurrence + if (occurrence == 0.0) 1.0 else REPEAT_WEIGHT

            if (key !in firstForm) {
                firstForm[key] = token
                firstIndex[key] = index

                // People lead with the subject — "the gradle build is broken",
                // not "broken is the gradle build" — so position is worth as
                // much as a repeat, and decays over the opening words only.
                scores[key] = scores[key]!! + earliness(index)
                scores[key] = scores[key]!! + specificity(token)
                // Demoted, not dropped: "build" opens half of all requests, but
                // "the gradle build" is a perfectly good title. Penalising lets
                // a real noun win while still letting these through when there
                // is nothing better in the sentence.
                if (key in REQUEST_WORDS) scores[key] = scores[key]!! - REQUEST_PENALTY
            }
        }

        if (scores.isEmpty()) return UNTITLED

        val head = scores.maxByOrNull { it.value }?.key ?: return UNTITLED
        awardAdjacency(head, tokens, positions, scores)

        // The head keeps its place unconditionally. The adjacency bonus is
        // awarded *by* it, so leaving the winner to be re-decided afterwards let
        // its own neighbours outscore it — "recipe app … recipe" came back as
        // "App Storage", with the word said twice missing entirely.
        val companions = scores.entries
            .filter { it.key != head }
            .sortedWith(
                compareByDescending<Map.Entry<String, Double>> { it.value }
                    // Ties go to whichever was said first, so the title reads in
                    // the order the sentence did.
                    .thenBy { firstIndex[it.key] ?: 0 },
            )
            .map { it.key }

        val chosen = (listOf(head) + companions)
            .take(maxWords)
            .sortedBy { firstIndex[it] ?: 0 }
            .map { firstForm[it] ?: it }

        return chosen.joinToString(" ") { display(it) }.ifBlank { UNTITLED }
    }

    /**
     * Favour the words sitting next to the strongest one.
     *
     * Titles are usually a phrase, not two unrelated words: "gradle build",
     * "camera preview", "login screen". Scoring words independently picked the
     * two highest-scoring ones wherever they happened to fall, which produced
     * things like "Gradle Keeps" — grammatical debris rather than a name.
     *
     * A request verb only earns this from the position *after* the head word,
     * where English puts the head of a compound noun. Before it, the same word
     * is just the request: "gradle build" is a thing, "napravi aplikaciju" is
     * an instruction, and only one of them belongs in a title.
     */
    private fun awardAdjacency(
        head: String,
        tokens: List<String>,
        positions: Map<String, List<Int>>,
        scores: MutableMap<String, Double>,
    ) {
        for (index in positions[head].orEmpty()) {
            for ((offset, neighbour) in listOf(index - 1, index + 1).withIndex()) {
                val token = tokens.getOrNull(neighbour) ?: continue
                val key = token.lowercase()
                if (key == head || key !in scores) continue
                val follows = offset == 1
                if (key in REQUEST_WORDS && !follows) continue
                scores[key] = scores[key]!! + ADJACENT_WEIGHT
            }
        }
    }

    /**
     * Splits into words, after removing the parts that are never a title.
     *
     * Code blocks go first: a message that pastes a stack trace is *about* the
     * one sentence around it, and left in, the title becomes whichever
     * identifier appears most often in the paste.
     */
    private fun tokenize(text: String): List<String> {
        val prose = text
            .replace(FENCED_CODE, " ")
            .replace(INLINE_CODE, " ")
            .replace(URL, " ")
        return prose.split(SPLIT).filter { it.isNotBlank() }.take(MAX_TOKENS)
    }

    /** Words that can carry a title: real words, or things like `build.gradle`. */
    private fun isCandidate(token: String): Boolean {
        if (token.length < MIN_LENGTH) {
            // Short is fine when it is obviously a name or an initialism — API,
            // SQL, C — and not when it is a stray letter.
            return token.length > 1 && token == token.uppercase() && token.any { it.isLetter() }
        }
        return token.any { it.isLetter() }
    }

    private fun earliness(index: Int): Double =
        if (index >= EARLY_WINDOW) 0.0 else EARLY_WEIGHT * (1.0 - index.toDouble() / EARLY_WINDOW)

    /**
     * How much this particular word narrows things down.
     *
     * A file name or a version number identifies a conversation almost on its
     * own; a capitalised word mid-sentence is usually a product or a person;
     * a long word is more specific than a short one. All small nudges — enough
     * to break a tie between "fix" and "AuraSocket", not enough to override a
     * word the user actually repeated.
     */
    private fun specificity(token: String): Double {
        var score = 0.0
        if (token.any { it.isDigit() }) score += TECHNICAL_WEIGHT
        if (token.contains('.') || token.contains('_') || token.contains('-')) {
            score += TECHNICAL_WEIGHT
        }
        // Internal capitals: camelCase or PascalCase, i.e. an identifier.
        if (token.drop(1).any { it.isUpperCase() }) score += TECHNICAL_WEIGHT
        else if (token.first().isUpperCase()) score += PROPER_NOUN_WEIGHT
        score += minOf(token.length, LENGTH_CAP) * LENGTH_WEIGHT
        return score
    }

    /**
     * Presentation casing.
     *
     * Identifiers keep the shape they were written in — `build.gradle` and
     * `AuraSocket` are wrong any other way — while ordinary words are
     * capitalised so the list reads as titles rather than as sentence
     * fragments.
     */
    private fun display(token: String): String {
        val cleaned = token.trim('.', ',', '-', '_', '\'', '"')
        if (cleaned.isEmpty()) return token
        val isIdentifier = cleaned.drop(1).any { it.isUpperCase() } ||
            cleaned.any { it.isDigit() } ||
            cleaned.contains('.') || cleaned.contains('_')
        if (isIdentifier || cleaned == cleaned.uppercase()) return cleaned
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private val FENCED_CODE = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE = Regex("`[^`]*`")
    private val URL = Regex("""https?://\S+""")

    /** Split on anything that is not a letter, digit, or identifier glue. */
    private val SPLIT = Regex("""[^\p{L}\p{N}._-]+""")

    private const val MIN_LENGTH = 3
    private const val MAX_TOKENS = 120
    private const val REPEAT_WEIGHT = 0.6
    private const val EARLY_WINDOW = 12
    private const val EARLY_WEIGHT = 1.0
    private const val TECHNICAL_WEIGHT = 0.5
    private const val PROPER_NOUN_WEIGHT = 0.35
    private const val LENGTH_WEIGHT = 0.04
    private const val LENGTH_CAP = 12
    private const val REQUEST_PENALTY = 0.6
    private const val ADJACENT_WEIGHT = 0.9

    /**
     * The verbs every request to an assistant opens with.
     *
     * Not dropped, because several are also perfectly good nouns — "the gradle
     * build", "the API check", "a fix for this". Scored down instead, so they
     * lose to anything more specific in the sentence and only surface when the
     * sentence has nothing else to offer.
     */
    private val REQUEST_WORDS: Set<String> = buildSet {
        addAll(
            """
            make fix add build create write help need want please try use using
            check show tell give get set run open start stop change update
            """.trimIndent().split(Regex("\\s+")),
        )
        addAll(
            // Serbian, including the first-person forms someone actually
            // dictates — "napravim" is far more common out loud than "napravi".
            """
            napravi napravim napravite uradi uradim uradis dodaj dodam
            popravi popravim kreiraj kreiram pisi napisi napisem pomozi
            hocu zelim treba trebam moram probaj probam koristi koristim
            proveri proverim pokazi reci daj uzmi postavi pokreni
            """.trimIndent().split(Regex("\\s+")),
        )
        remove("")
    }

    /**
     * Words that never describe a conversation.
     *
     * English and Serbian together, and the Serbian side is written in Latin
     * script because that is what the device's speech recogniser returns.
     */
    private val STOP_WORDS: Set<String> = buildSet {
        addAll(
            // English — articles, pronouns, auxiliaries, prepositions.
            """
            a an the and or but if then than that this these those there here
            i me my mine you your yours he him his she her hers it its we us our
            ours they them their what which who whom whose when where why how
            is am are was were be been being do does did doing done have has had
            having will would shall should can could may might must
            of in on at to for with without from by about into over under again
            further once all any both each few more most other some such no nor
            not only own same so too very just now also as up down out off
            """.trimIndent().split(Regex("\\s+")),
        )
        addAll(
            // Serbian (Latin) — the same classes of word.
            """
            i ili ali ako onda nego taj ta to ti te tu ovaj ova ovo ovde tamo
            ja me mene moj moja moje ti tebe tvoj on njega njegov ona nje njen
            mi nas nas vi vas vas oni ona njih njihov sta koji koja koje ko kome
            kada gde zasto kako je su bio bila bilo biti sam si smo ste jesu
            imam imas ima imamo imate imaju bice hoce nece treba mora moze mogu
            od u na za sa bez iz po pri kroz preko ispod iznad opet sve svaki
            neki drugi vise najvise tako samo bas sad takodje kao gore dole
            da ne nema li se svoj svoja svoje jos onaj ono ovim tim jedan jedna
            """.trimIndent().split(Regex("\\s+")),
        )
        // "molim" — Serbian "please" — is pure politeness and never a noun,
        // so unlike the rest of the request verbs it is dropped outright.
        add("molim")
        remove("")
    }
}
