package com.example.match3puzzlegame

// --- Importuri (Asigură-te că le ai pe toate necesare din pașii anteriori) ---
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon // Pentru iconiță back 
import androidx.compose.material3.IconButton // Pentru iconiță back 
import androidx.compose.material3.Divider // Separator vizual 
import androidx.compose.material3.* // Import Material 3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.match3puzzlegame.ui.theme.Match3PuzzleGameTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.systemBarsPadding // Importă modifier-ul
import android.content.Context // Pentru a accesa resursele
import android.media.MediaPlayer // Pentru redare audio
import androidx.compose.ui.platform.LocalContext // Pentru a obține contextul în Composable
import androidx.compose.foundation.rememberScrollState // Pentru starea scroll-ului
import androidx.compose.foundation.verticalScroll // Pentru modifier-ul de scroll






//Constante si Data classes

data class Recipe(
    val id: Int, // Identificator unic
    val name: String,
    val description: String,
    val ingredientsNeeded: Map<Int, Int>,
    val sellingPrice: Int
)

data class TilePosition(val row: Int, val col: Int)

// Reprezintă un singur obiectiv pentru un nivel
data class LevelObjective(
    val type: ObjectiveType,
    val targetId: Int, // Poate fi IngredientType, RecipeId sau 0 pentru scor
    val targetQuantity: Int // Cantitatea/Numărul necesar
)

// Reprezintă datele complete pentru un nivel
data class LevelData(
    val levelId: Int,
    val name: String,
    val objectives: List<LevelObjective>,
    val maxMoves: Int,
    val unlocksRecipeIds: List<Int> = emptyList() //  Listă de ID-uri rețete deblocate
)

// Tipuri posibile de obiective
enum class ObjectiveType {
    COLLECT_INGREDIENTS, // Colectează un număr specific dintr-un ingredient
    COOK_RECIPES,       // Gătește o rețetă specifică de un număr de ori
    REACH_SCORE         // Atinge un anumit scor
    // TODO: Adaugă alte tipuri (ex: CLEAR_BLOCKERS - curăță piese speciale)
}




// --- Constante Globale ---
const val ROWS = 8
const val COLS = 8
const val META_COST = 100
const val EMPTY_TILE = 0 // Important pentru logică
const val TILE_TYPE_1 = 1
const val TILE_TYPE_2 = 2
const val TILE_TYPE_3 = 3
const val TILE_TYPE_4 = 4
const val TILE_TYPE_5 = 5
val TILE_TYPES = listOf(TILE_TYPE_1, TILE_TYPE_2, TILE_TYPE_3, TILE_TYPE_4, TILE_TYPE_5)
private const val TAG = "Match3Game" // TAG pentru Logcat




//Map-uri

// Definirea explicită a tipului Map-ului
val tileColors: Map<Int, Color> = mapOf(
    EMPTY_TILE to Color.Transparent,
    TILE_TYPE_1 to Color.Red.copy(alpha = 0.8f),
    TILE_TYPE_2 to Color(0xFFFFA500), // Orange
    TILE_TYPE_3 to Color.Blue.copy(alpha = 0.8f),
    TILE_TYPE_4 to Color.Green.copy(alpha = 0.8f),
    TILE_TYPE_5 to Color.Magenta.copy(alpha = 0.8f)
)

val tileDrawables: Map<Int, Int> = mapOf(
    TILE_TYPE_1 to R.drawable.castravete, // Înlocuiește cu numele reale ale fișierelor tale!
    TILE_TYPE_2 to R.drawable.rosie,
    TILE_TYPE_3 to R.drawable.ceapa,
    TILE_TYPE_4 to R.drawable.porumb,
    TILE_TYPE_5 to R.drawable.cartof
)






//Liste

// --- Date Nivele Inițiale ---
val gameLevels = listOf(
    LevelData(
        levelId = 1,
        name = "Piața Locală - Începuturi",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_2, 15) // Colectează 15 Roșii
        ),
        maxMoves = 20,
        unlocksRecipeIds = listOf(2)
    ),
    LevelData(
        levelId = 2,
        name = "Prima Comandă - Salata",
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 1, 1) // Gătește Salata Proaspătă (ID 1) o dată
        ),
        maxMoves = 25,
        unlocksRecipeIds = listOf(3)
    ),
    LevelData(
        levelId = 3,
        name = "Provizia de Iarnă",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_5, 20), // 20 Cartofi
            LevelObjective(ObjectiveType.REACH_SCORE, 0, 5000) // Atinge 5000 puncte
        ),
        maxMoves = 30,
        unlocksRecipeIds = listOf(4)
    ),
    LevelData(
        levelId = 4,
        name = "Festivalul Recoltei",
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 2, 2), // Gătește Garnitura de Porumb de 2 ori
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_4, 30) // 30 Porumb
        ),
        maxMoves = 35,
        unlocksRecipeIds = listOf(5) // Deblochează Supa Cremă (4)
    )



    // Adaugă mai multe nivele
)

val allPossibleRecipes = listOf(
    Recipe(
        id = 1,
        name = "Salată Proaspătă",
        description = "Perfectă pentru o zi de vară.",
        ingredientsNeeded = mapOf(TILE_TYPE_1 to 5, TILE_TYPE_2 to 3, TILE_TYPE_3 to 2), // 5 Castraveți, 3 Roșii, 2 Cepe
        sellingPrice = 30
    ),
    Recipe(
        id = 2,
        name = "Garnitură de Porumb",
        description = "Simplu și gustos.",
        ingredientsNeeded = mapOf(TILE_TYPE_4 to 8, TILE_TYPE_1 to 2), // 8 Porumb, 2 Castraveți
        sellingPrice = 45
    ),
    Recipe(
        id = 3,
        name = "Tocăniță de Legume",
        description = "Sățioasă și aromată.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 6, TILE_TYPE_2 to 4, TILE_TYPE_3 to 3), // 6 Cartofi, 4 Roșii, 3 Cepe
        sellingPrice = 70
        ),
    // --- Adaugă mai multe rețete aici ---
    Recipe(
        id = 4,
        name = "Supă Cremă de Roșii",
        description = "Clasică și reconfortantă.",
        ingredientsNeeded = mapOf(TILE_TYPE_2 to 10, TILE_TYPE_3 to 4), // 10 Roșii, 4 Cepe
        sellingPrice = 55
        ),
    Recipe(
        id = 5,
        name = "Cartofi la Cuptor",
        description = "Cu ierburi aromatice.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 12, TILE_TYPE_3 to 2), // 12 Cartofi, 2 Cepe
        sellingPrice = 60
        )
)
val initialRecipes = allPossibleRecipes.filter { it.id == 1 }





//Funcții globale pure

fun getIngredientName(tileType: Int): String {
    return when (tileType) {
        TILE_TYPE_1 -> "Cucumber" // Exemplu
        TILE_TYPE_2 -> "Tomato" // Exemplu
        TILE_TYPE_3 -> "Onion" // Exemplu
        TILE_TYPE_4 -> "Corn" // Exemplu
        TILE_TYPE_5 -> "Potato" // Exemplu
        else -> "Necunoscut"
    }
}

// --- Funcție Helper pentru Redare Sunet ---
private fun playSound(context: Context, soundResourceId: Int) {
    // Folosește try-catch pentru a evita crash-uri dacă resursa nu e găsită sau apare altă eroare
    try {
        // Creează un MediaPlayer nou PENTRU FIECARE redare a unui sunet scurt.
        // Nu refolosi același obiect MediaPlayer pentru sunete scurte rapide,
        // poate cauza probleme de suprapunere sau întârzieri.
        val mp = MediaPlayer.create(context, soundResourceId)
        if (mp == null) {
            Log.e(TAG, "playSound: MediaPlayer.create returned null for resource ID: $soundResourceId")
            return
        }
        mp.setOnCompletionListener { mediaPlayer ->
            // Eliberează resursele MediaPlayer DUPĂ ce sunetul s-a terminat
            mediaPlayer?.release()
            Log.d(TAG, "playSound: MediaPlayer released for resource ID: $soundResourceId")
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "playSound: MediaPlayer error! what: $what, extra: $extra for resource ID: $soundResourceId")
            // Încearcă să eliberezi resursele și în caz de eroare
            mp?.release()
            true // Indică faptul că am gestionat eroarea
        }
        mp.start() // Pornește redarea
    } catch (e: Exception) {
        Log.e(TAG, "playSound: Exception while trying to play sound ID: $soundResourceId", e)
    }
}






// --- MainActivity  ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Match3PuzzleGameTheme {
                Match3GameApp() // Apelăm Composable-ul principal care deține starea
            }
        }
    }
}


// --- Composable Părinte care Deține Starea și Logica ---
@Composable
fun Match3GameApp() {
    // === STAREA JOCULUI (Mutată aici) ===
    var score by remember { mutableStateOf(0) }
    var inventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var feedbackMessage by remember { mutableStateOf("") }
    // Inițializare board folosind funcția DUPĂ ce e definită
    var board by remember { mutableStateOf(emptyList<MutableList<Int>>()) } // Inițial goală
    var selectedTilePos by remember { mutableStateOf<TilePosition?>(null) }
    var tilesBeingMatched by remember { mutableStateOf<Set<TilePosition>>(emptySet()) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var swappingTiles by remember { mutableStateOf<Pair<TilePosition, TilePosition>?>(null) }
    val tile1Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    val tile2Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var swapAnimationFinished by remember { mutableStateOf(true) }
    var currentLevelIndex by remember { mutableStateOf(0) }
    val currentLevelData = remember(currentLevelIndex) { gameLevels.getOrNull(currentLevelIndex) }
    var movesLeft by remember { mutableStateOf(0) } // Va fi setat în LaunchedEffect
    var objectiveProgress by remember { mutableStateOf<Map<LevelObjective, Int>>(emptyMap()) }
    var gameState by remember { mutableStateOf("Playing") }
    var selectedRecipeToShow by remember { mutableStateOf<Recipe?>(null) } // Pentru dialog
    var showRecipeBookScreen by remember { mutableStateOf(false) } // Pentru navigare ecran
    var availableRecipes by remember { mutableStateOf(initialRecipes.toMutableList()) } // Lista rețetelor
    val context = LocalContext.current // Obține contextul aici
    var playerXP by remember { mutableStateOf(0) } // --- Starea pentru Experiență ---
    var playerMoney by remember { mutableStateOf(100) }
    var cookedMealsInventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }  // --- Stare pentru Mâncarea Gătită, gata de vânzare ---
    var showShopDialog by remember { mutableStateOf(false) }


    // === LOGICA JOCULUI  ===




    // ---  Funcție pentru Vânzarea Mâncărurilor ---
    fun sellCookedMeals() {
        if (cookedMealsInventory.isEmpty()) {
            Log.d(TAG, "Sell attempt but cooked inventory is empty.")
            feedbackMessage = "Nu ai ce vinde!"
            return
        }

        var moneyEarned = 0
        cookedMealsInventory.forEach { (recipeId, quantity) ->
            // Găsește prețul rețetei
            val recipe = allPossibleRecipes.find { it.id == recipeId } // Folosim lista globală
            if (recipe != null) {
                moneyEarned += recipe.sellingPrice * quantity
            } else {
                Log.w(TAG, "Could not find recipe data for ID $recipeId during selling.")
            }
        }

        Log.d(TAG, "Selling all cooked meals. Earned: $moneyEarned Money.")

        // Actualizează banii jucătorului
        playerMoney += moneyEarned

        // Golește inventarul de mâncare gătită
        cookedMealsInventory = emptyMap()

        // Oferă feedback
        feedbackMessage = "Ai vândut marfa pentru $moneyEarned Bani!"

        // TODO: Redă un sunet de "casa de marcat" sau similar
        playSound(context, R.raw.coin) // Folosim sunetul de monedă existent? Sau altul?
    }



    fun findMatchesOnBoard(targetBoard: List<List<Int>>): Set<TilePosition> {
        val matches = mutableSetOf<TilePosition>()
        for (r in 0 until ROWS) {
            var currentStreak = 1
            var currentType = -1 // Tip invalid inițial
            for (c in 0 until COLS) {
                val tileType =
                    targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) {
                            matches.add(TilePosition(r, c - i))
                        }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) {
                    matches.add(TilePosition(r, COLS - i))
                }
            }
        }
        // Verificare Verticală
        for (c in 0 until COLS) {
            var currentStreak = 1
            var currentType = -1
            for (r in 0 until ROWS) {
                val tileType =
                    targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE // Folosește targetBoard
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) {
                            matches.add(TilePosition(r - i, c))
                        }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) {
                    matches.add(TilePosition(ROWS - i, c))
                }
            }
        }
        return matches
    }


    fun generateValidInitialBoard(): List<MutableList<Int>> {
        var attempts = 0
        while (attempts < 100) { // Adăugăm o limită de siguranță pentru a evita bucle infinite
            Log.d(TAG, "Generating initial board attempt: ${attempts + 1}")
            // 1. Generează o tablă candidată
            val candidateBoard = List(ROWS) {
                MutableList(COLS) { TILE_TYPES.random() }
            }

            // 2. Verifică potrivirile pe tabla candidată
            val initialMatches = findMatchesOnBoard(candidateBoard)

            // 3. Dacă nu sunt potriviri, returnează tabla validă
            if (initialMatches.isEmpty()) {
                Log.d(TAG, "Valid initial board found after ${attempts + 1} attempts.")
                return candidateBoard // Am găsit o tablă bună!
            }

            // 4. Dacă există potriviri, bucla continuă și generăm alta
            attempts++
            Log.d(TAG, "Initial board had matches, retrying...")
        }
        // Fallback: Dacă nu găsim o tablă validă după multe încercări,
        // returnăm ultima generată (cu potriviri) pentru a evita blocarea.
        Log.w(
            TAG,
            "Could not generate a match-free initial board after 100 attempts. Using last generated board."
        )
        // Să returnăm totuși o tablă goală în acest caz extrem pentru a fi clar
        return List(ROWS) { MutableList(COLS) { EMPTY_TILE } } // Sau returnează ultima `candidateBoard`
    }


    // Inițializarea reală a tablei DUPĂ definirea funcțiilor necesare
    LaunchedEffect(Unit) { // Rulează o singură dată la început
        board = generateValidInitialBoard()
    }


    fun applyGravityToBoard(targetBoard: List<MutableList<Int>>): List<MutableList<Int>> {
        Log.d(TAG, "Applying gravity logic...")
        val newBoard = targetBoard.map { it.toMutableList() } // Copie mutabilă
        for (c in 0 until COLS) {
            val column = mutableListOf<Int>()
            // Adună toate piesele non-goale din coloană
            for (r in 0 until ROWS) {
                if (newBoard[r][c] != EMPTY_TILE) { // Folosește newBoard
                    column.add(newBoard[r][c])
                }
            }
            val emptyToAdd = ROWS - column.size
            for (r in 0 until ROWS) {
                newBoard[r][c] =
                    if (r < emptyToAdd) EMPTY_TILE else column[r - emptyToAdd] // Modifică newBoard
            }
        }
        Log.d(TAG, "Gravity logic finished.")
        return newBoard // Returnează tipul corect
    }


    fun fillEmptyTilesOnBoard(targetBoard: List<MutableList<Int>>): List<MutableList<Int>> {
        Log.d(TAG, "Filling empty tiles logic...")
        val newBoard = targetBoard.map { it.toMutableList() }
        var filledAny = false
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (newBoard[r][c] == EMPTY_TILE) { // Verifică în copie
                    newBoard[r][c] = -(TILE_TYPES.random()) // Valoare negativă!
                    filledAny = true
                }
            }
        }
        Log.d(TAG, "Fill logic finished. Filled any: $filledAny")
        // Returnăm copia modificată (sau originalul dacă nu s-a umplut nimic, deși copia e mai sigură)
        return newBoard
    }


    fun checkLevelEndCondition(progressToCheck: Map<LevelObjective, Int> = objectiveProgress) {
        if (gameState != "Playing") return // Nu verifica dacă jocul s-a terminat deja

        if (currentLevelData == null) {
            Log.w(TAG, "checkLevelEndCondition called with null level data!")
            return
        }

        // --- LOG ÎNCEPUT VERIFICARE ---
        Log.d(TAG, "--- Checking Level End Condition for Level ${currentLevelData.levelId} ---")
        // --- LOG STARE PROGRES ACTUAL ---
        Log.d(TAG, "Checking with Progress State: $progressToCheck")

        // Verifică dacă TOATE obiectivele sunt îndeplinite
        var allMet = true // Presupunem adevărat inițial
        currentLevelData.objectives.forEach { objective -> // Iterează prin fiecare obiectiv al nivelului
            val progress = progressToCheck[objective] ?: 0
            val target = objective.targetQuantity
            val isMet = progress >= target
            Log.d(
                TAG,
                "Checking Objective: Type=${objective.type}, TargetID=${objective.targetId}, TargetQty=$target, CurrentProgress=$progress, IsMet=$isMet"
            )
            if (!isMet) {
                allMet = false
            }
        }

        // --- LOG REZULTAT FINAL VERIFICARE OBIECTIVE ---
        Log.d(TAG, "Final check: All Objectives Met = $allMet")

        // --- Restul logicii (rămâne la fel, dar acum știm sigur valoarea lui allMet) ---
        if (allMet) {
            // --- CONDIȚIE DE VICTORIE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} WON!")
            gameState = "Won"

            // --- Deblochează Rețete ---
            val newlyUnlockedRecipes = mutableListOf<String>()
            currentLevelData.unlocksRecipeIds.forEach { recipeId ->
                Log.d(TAG, "Checking unlock for Recipe ID: $recipeId") // Log existent, e ok
                if (availableRecipes.none { it.id == recipeId }) {
                    Log.d(TAG, "Recipe ID $recipeId is NOT already available.") // Log existent, e ok
                    allPossibleRecipes.find { it.id == recipeId }?.let { recipeToAdd ->
                        Log.d(TAG, "Found recipe to add: ${recipeToAdd.name}") // Log existent, e ok
                        availableRecipes = (availableRecipes + recipeToAdd).toMutableList()
                        newlyUnlockedRecipes.add(recipeToAdd.name)
                        Log.i(TAG, "Unlocked recipe: ${recipeToAdd.name}. New available list size: ${availableRecipes.size}") // Log existent, e ok
                    } ?: Log.w(TAG, "Recipe ID $recipeId to unlock not found in allPossibleRecipes!") // Log existent, e ok
                } else {
                    Log.d(TAG, "Recipe ID $recipeId IS already available. Skipping.") // Log existent, e ok
                }
            }

            var winMessage = "Nivel ${currentLevelData.levelId} Terminat!"
            if (newlyUnlockedRecipes.isNotEmpty()) {
                winMessage += "\nRețete noi: ${newlyUnlockedRecipes.joinToString()}"
            }
            feedbackMessage = winMessage // Setează mesajul de victorie
            playSound(context, R.raw.win) // Redă sunetul de victorie

        } else if (movesLeft <= 0) {
            // --- CONDIȚIE DE ÎNFRÂNGERE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} LOST! No moves left.")
            gameState = "Lost"
            playSound(context, R.raw.lost) // Redă sunetul de înfrângere
            feedbackMessage = "Ai rămas fără mutări! Reîncearcă!"

        } else {
            // --- Nivelul Continuă ---
            // Am eliminat logul de aici pentru că îl avem mai sus ("Final check: All Objectives Met = false")
            // Log.d(TAG, "Level continues. Moves left: $movesLeft. Objectives met: $allMet")
        }
    }

    // suspend fun processMatchesAndCascades() { /* ... codul funcției, actualizează score, inventory, objectiveProgress, board, etc. Apelează checkLevelEndCondition */ }
    suspend fun processMatchesAndCascades() {

        var currentBoard = board
        var cascadeCount = 0
        var basePointsThisMatch = 0
        var cascadeMultiplier = 1.0 // Multiplicator inițial
        var totalScoreEarnedThisTurn = 0 // Scorul total adunat în toate cascadele


        while (true) { // Bucla cascadei
            val matches = findMatchesOnBoard(currentBoard) // Găsește potriviri pe tabla curentă

            if (matches.isEmpty()) {
                Log.d(TAG, "No more matches found, ending cascade loop.")
                if (totalScoreEarnedThisTurn > 0) {
                    score += totalScoreEarnedThisTurn
                    Log.d(
                        TAG,
                        "Total score earned this turn: $totalScoreEarnedThisTurn. New global score: $score"
                    )
                    // --- *NOU* Actualizează progresul pentru obiectivele de scor DUPĂ actualizarea scorului ---
                    val updatedProgress = objectiveProgress.toMutableMap()
                    currentLevelData?.objectives?.forEach { objective ->
                        if (objective.type == ObjectiveType.REACH_SCORE) {
                            // Actualizează progresul cu scorul curent, limitat la țintă
                            updatedProgress[objective] =
                                score.coerceAtMost(objective.targetQuantity)
                        }
                    }
                    objectiveProgress = updatedProgress // Aplică actualizările de progres
                    feedbackMessage = "Ai câștigat în total $totalScoreEarnedThisTurn puncte!"
                    delay(800L)
                }
                checkLevelEndCondition()
                break
            }

            playSound(context, R.raw.potrivire)
            tilesBeingMatched = matches
            cascadeCount++
            Log.d(TAG, "Cascade $cascadeCount: Found ${matches.size} matched tiles.")
            basePointsThisMatch = 0

            val ingredientsEarnedThisMatch = mutableMapOf<Int, Int>()
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    val tileType = currentBoard.getOrNull(pos.row)?.getOrNull(pos.col)
                    if (tileType != null && tileType != EMPTY_TILE) {
                        ingredientsEarnedThisMatch[tileType] =
                            ingredientsEarnedThisMatch.getOrDefault(tileType, 0) + 1

                        // Adaugă puncte de bază
                        basePointsThisMatch += 10 // Exemplu: 10 puncte per piesă
                    }
                }
            }
            // --- *NOU* Actualizează progresul pentru obiectivele de colectare DUPĂ actualizarea inventarului ---
            val updatedProgress = objectiveProgress.toMutableMap() // Ia progresul curent
            currentLevelData?.objectives?.forEach { objective ->
                if (objective.type == ObjectiveType.COLLECT_INGREDIENTS) {
                    val ingredientId = objective.targetId
                    // Adună cantitatea NOUĂ colectată în această potrivire la progresul existent
                    val collectedNow = ingredientsEarnedThisMatch.getOrDefault(ingredientId, 0)
                    if (collectedNow > 0) {
                        val currentProg = updatedProgress[objective] ?: 0
                        // Actualizează progresul, limitat la țintă
                        updatedProgress[objective] =
                            (currentProg + collectedNow).coerceAtMost(objective.targetQuantity)
                    }
                }
                // Verificăm obiectivele de scor după actualizarea scorului global (mai sus)
            }
            objectiveProgress = updatedProgress



            if (matches.size >= 5) {
                basePointsThisMatch += 100
                Log.d(TAG, "Bonus 5+ match applied!")
            } else if (matches.size == 4) {
                basePointsThisMatch += 50 // Bonus pentru 4
                Log.d(TAG, "Bonus 4 match applied!")
            }

            val pointsThisCascade = (basePointsThisMatch * cascadeMultiplier).toInt()
            totalScoreEarnedThisTurn += pointsThisCascade

            Log.d(
                TAG,
                "Cascade $cascadeCount: Base Points=$basePointsThisMatch, Multiplier=$cascadeMultiplier, Points This Cascade=$pointsThisCascade"
            )


            val currentInventory = inventory.toMutableMap()
            ingredientsEarnedThisMatch.forEach { (ingredientId, quantity) ->
                currentInventory[ingredientId] =
                    currentInventory.getOrDefault(ingredientId, 0) + quantity
            }
            inventory = currentInventory
            Log.d(TAG, "Inventory updated: $inventory")
            val feedbackParts =
                ingredientsEarnedThisMatch.map { "+${it.value} ${getIngredientName(it.key)}" }
            val scoreFeedback = "+$pointsThisCascade puncte!"
            feedbackMessage = if (cascadeCount > 1) {
                "Cascadă $cascadeCount! ${feedbackParts.joinToString()} $scoreFeedback"
            } else {
                "Potrivire! ${feedbackParts.joinToString()} $scoreFeedback"
            }

            cascadeMultiplier += 0.5


            // --- 1. Procesează potrivirile (calcul scor, pregătește golirea) ---

            val boardWithEmptyTiles = currentBoard.map { it.toMutableList() }
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    boardWithEmptyTiles[pos.row][pos.col] = EMPTY_TILE
                }
            }


            // --- 2. Animație dispariție & Actualizare UI ---
            delay(400L) // Așteaptă vizual dispariția (timp similar cu animația CSS)
            val boardAfterMatch = currentBoard.map { it.toMutableList() }
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    boardAfterMatch[pos.row][pos.col] = EMPTY_TILE
                }
            }
            tilesBeingMatched = emptySet()
            board = boardAfterMatch // Actualizează starea principală PENTRU a arăta spațiile goale
            currentBoard = boardAfterMatch // Continuăm procesarea de la această stare


            // --- 3. Aplică Gravitația ---
            val boardAfterGravity =
                applyGravityToBoard(currentBoard) // Funcție nouă care returnează tabla modificată
            delay(300L)
            board = boardAfterGravity
            currentBoard = boardAfterGravity


            // ---4. Umple spațiile goale ---
            val boardAfterFill =
                fillEmptyTilesOnBoard(currentBoard) // Funcție nouă care returnează tabla modificată
            delay(400L)
            board = boardAfterFill
            currentBoard = boardAfterFill

            val boardCleaned = currentBoard.map { row ->
                row.map { item -> abs(item) }.toMutableList() // Transformă totul în pozitiv
            }
            board = boardCleaned // Actualizează starea finală cu valori pozitive
            currentBoard = boardCleaned // Continuă verificarea cu tabla curățată
            Log.d(TAG, "Cleaned negative tile markers.")
            Log.d(TAG, "End of cascade $cascadeCount processing loop. Checking for more matches...")
        }

        if (totalScoreEarnedThisTurn > 0) {
            // Sunet opțional pentru adunarea scorului
            // playSound(context, R.raw.score_tick) // Exemplu
            score += totalScoreEarnedThisTurn
        }
        Log.d(TAG, "processMatchesAndCascades finished.")
    }


    // --- Funcție Helper pentru Adiacență ---
    fun areAdjacent(pos1: TilePosition, pos2: TilePosition): Boolean {
        val rowDiff = abs(pos1.row - pos2.row)
        val colDiff = abs(pos1.col - pos2.col)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }

    //fun swapTiles(pos1: TilePosition, pos2: TilePosition) { /* ... codul funcției, actualizează movesLeft, board, isProcessing, etc. Apelează processMatchesAndCascades sau checkLevelEndCondition */ }
    fun swapTiles(pos1: TilePosition, pos2: TilePosition) {
        Log.d(TAG, "Entering swapTiles. isProcessing=$isProcessing, gameState=$gameState, movesLeft=$movesLeft")
        if (isProcessing || gameState != "Playing") {
            Log.d(TAG, "Swap ignorat: isProcessing=$isProcessing, gameState=$gameState")
            return
        }

        Log.d(TAG, "Attempting swap between $pos1 and $pos2")

        // 1. Creează noua tablă cu piesele inversate
        val boardAfterSwap = board.map { it.toMutableList() }
        val temp = boardAfterSwap[pos1.row][pos1.col]
        boardAfterSwap[pos1.row][pos1.col] = boardAfterSwap[pos2.row][pos2.col]
        boardAfterSwap[pos2.row][pos2.col] = temp

        // 2. Verifică *potențialele* potriviri DUPĂ swap (fără a modifica starea încă)
        val potentialMatches = findMatchesOnBoard(boardAfterSwap) // Folosim o funcție ce primește tabla

        if (movesLeft > 0) {
            movesLeft = movesLeft - 1
            Log.d(TAG, "Move consumed. Moves left NOW: $movesLeft")
        } else {
            Log.d(TAG, "No moves left, swap ignored effectively for game end check")
            selectedTilePos = null
            checkLevelEndCondition() // Verifică starea aici
            return
        }



        if (potentialMatches.isNotEmpty()) {
            // --- Swap valid - pornește procesarea ---
            Log.d(TAG, "Swap valid, starting processing coroutine")
            feedbackMessage = "" // Resetează feedback-ul
            selectedTilePos = null // Deselectează vizual
            isProcessing = true // Blochează input-ul

            // Actualizează starea pentru a ARĂTA swap-ul
            board = boardAfterSwap

            // Lansează corutina pentru procesarea cascadei
            scope.launch {
                processMatchesAndCascades() // Rulează ciclul complet
                checkLevelEndCondition() // ---  Verifică finalul nivelului DUPĂ procesare ---
                isProcessing = false // Deblochează input-ul la sfârșit
                Log.d(TAG, "Processing finished.")
            }
        } else {
            // --- Swap invalid - nu face nimic vizual pe termen lung ---
            // (Am putea adăuga o animație scurtă de "shake" aici)
            Log.d(TAG, "Swap invalid (no matches formed), but move consumed.")
            feedbackMessage = "Fără potrivire..." // Mesaj mai neutru
            selectedTilePos = null

            // --- *NOU* Animație de "shake back" (Opțional - implementare ulterioară) ---
            // Aici ai putea adăuga o mică animație care arată piesele făcând swap și revenind rapid.
            // --- *NOU* Verifică finalul nivelului DUPĂ mutarea invalidă ---
            checkLevelEndCondition() // Verifică dacă a fost ultima mutare
        }
    }

    // --- Funcție Helper pentru Verificare Gătit ---
    fun canCookRecipe(recipe: Recipe): Boolean {
        // Verifică fiecare ingredient necesar
        recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
            val quantityOwned = inventory.getOrDefault(ingredientId, 0) // Cât deține jucătorul
            if (quantityOwned < quantityNeeded) {
                // Dacă lipsește chiar și un singur ingredient, nu se poate găti
                return false
            }
        }
        // Dacă am ajuns aici, înseamnă că toate ingredientele sunt suficiente
        return true
    }


    fun cookRecipe(recipe: Recipe) {
        if (!canCookRecipe(recipe)) {
            Log.w(TAG, "Attempted to cook ${recipe.name} without enough ingredients!")
            feedbackMessage = "Nu ai suficiente ingrediente pentru ${recipe.name}!"
            return // Nu continua dacă nu se poate găti
        }

        Log.d(TAG, "Cooking recipe: ${recipe.name}")

        // 1. Consumă ingredientele
        val updatedInventory = inventory.toMutableMap() // Copie mutabilă
        recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
            val currentQuantity = updatedInventory.getOrDefault(ingredientId, 0)
            // Scade cantitatea necesară (asigură-te că nu scazi sub 0, deși canCook a verificat)
            updatedInventory[ingredientId] = (currentQuantity - quantityNeeded).coerceAtLeast(0)
        }
        inventory = updatedInventory // Actualizează starea inventarului
        playSound(context, R.raw.gatire) // --- Redă sunetul de gătit ---

        val xpGained = 50 // Exemplu: 50 XP per rețetă
        playerXP += xpGained
        Log.d(TAG, "Gained $xpGained XP. Total XP: $playerXP")

        // ---  Acordă Monedă ---

        val currentCookedAmount = cookedMealsInventory.getOrDefault(recipe.id, 0)
        val updatedCookedInventory = cookedMealsInventory.toMutableMap()
        updatedCookedInventory[recipe.id] = currentCookedAmount + 1
        cookedMealsInventory = updatedCookedInventory // Actualizează starea cookedMealsInventory
        Log.d(TAG, "Added ${recipe.name} to cooked inventory. New cooked inv: $cookedMealsInventory")

        val updatedProgress = objectiveProgress.toMutableMap() // Copie curentă
        currentLevelData?.objectives?.forEach { objective ->
            if (objective.type == ObjectiveType.COOK_RECIPES && objective.targetId == recipe.id) {
                val currentProg = updatedProgress[objective] ?: 0
                updatedProgress[objective] = (currentProg + 1).coerceAtMost(objective.targetQuantity)
                Log.d(TAG, "Cook objective progress for ${recipe.name}: ${updatedProgress[objective]}/${objective.targetQuantity}")
            }
        }
        objectiveProgress = updatedProgress

        feedbackMessage = "Ai gătit ${recipe.name}! +$xpGained XP"
        selectedRecipeToShow = null

        // ---  Apelează verificarea CU progresul actualizat ---
        checkLevelEndCondition(updatedProgress) // Pasează map-ul actualizat
    }



 // --- Resetare la începutul nivelului ---
    LaunchedEffect(currentLevelIndex) {
        Log.d(TAG, "--- LaunchedEffect triggered for level: ${currentLevelData?.levelId} ---") // LOG NOU
        val levelData = gameLevels.getOrNull(currentLevelIndex) // Obține datele aici
        if (levelData != null) {
            Log.d(TAG, "Resetting state for Level ${levelData.levelId}")
            // Resetează starea pentru noul nivel
            board = generateValidInitialBoard()
            movesLeft = levelData.maxMoves // Folosește levelData obținut local
            Log.d(TAG, "movesLeft reset to: ${levelData.maxMoves}")
            objectiveProgress = levelData.objectives.associateWith { 0 }
            inventory = emptyMap()
            // ... restul resetărilor ...
            score = 0
            gameState = "Playing"
        } else {
            Log.e(TAG, "Invalid level index: $currentLevelIndex")
            feedbackMessage = "Felicitări, ai terminat toate nivelele!"
            gameState = "Finished"
        }
    }


    // LaunchedEffect pentru animația de swap (rămâne aici)
    // LaunchedEffect(swappingTiles) { /* ... codul funcției, animază offset, apelează swapTiles, actualizează swappingTiles, swapAnimationFinished */ }
    // --- Efect pentru a rula animația de SWAP (Varianta Corectată cu Job.join()) ---
    LaunchedEffect(swappingTiles) {
        val tiles = swappingTiles
        if (tiles != null) {
            Log.d(TAG, "LaunchedEffect: Animating swap for $tiles")
            playSound(context, R.raw.swap)
            val (pos1, pos2) = tiles
            val xDiff = (pos2.col - pos1.col)
            val yDiff = (pos2.row - pos1.row)
            // Lansăm animațiile și PĂSTRĂM referințele la Job-uri
            val job1 = scope.launch {
                tile1Offset.snapTo(IntOffset.Zero)
                tile1Offset.animateTo(
                    targetValue = IntOffset(x = xDiff, y = yDiff),
                    animationSpec = tween(durationMillis = 300) // Poți ajusta durata aici
                )
                tile1Offset.snapTo(IntOffset.Zero) // Resetăm la finalul animației job-ului
            }
            val job2 = scope.launch {
                tile2Offset.snapTo(IntOffset.Zero)
                tile2Offset.animateTo(
                    targetValue = IntOffset(x = -xDiff, y = -yDiff),
                    animationSpec = tween(durationMillis = 300) // Poți ajusta durata aici
                )
                tile2Offset.snapTo(IntOffset.Zero) // Resetăm la finalul animației job-ului
            }

            // --- Așteaptă ca AMBELE animații să se termine ---
            Log.d(TAG, "Waiting for swap animations to join...")
            job1.join() // Așteaptă finalizarea job1
            job2.join() // Așteaptă finalizarea job2
            Log.d(TAG, "Swap animations joined.")

            // --- Continuă DUPĂ ce animațiile s-au terminat ---
            Log.d(TAG, "Swap animation visually complete. Proceeding with logic.")
            swapTiles(pos1, pos2) // ACUM apelăm logica reală
            swappingTiles = null // Resetează starea de swap
            swapAnimationFinished = true // Marchează finalul animației
            Log.d(TAG, "Swap logic processing initiated, state reset.")

        }
    }

    // === Decizia de Afișare ===
    if (showRecipeBookScreen) {
        RecipeBookScreen(
            recipes = availableRecipes,
            inventory = inventory, // Pasează starea
            canCookChecker = ::canCookRecipe, // Pasează referința la funcție
            onCookRecipe = ::cookRecipe,      // Pasează referința la funcție
            onShowRecipeDetails = { recipe -> selectedRecipeToShow = recipe }, // Setează starea pt dialog
            onClose = { playSound(context, R.raw.click); showRecipeBookScreen = false } // Modifică starea de navigare
        )
    } else {
        GameScreen(
            // Date de afișat
            score = score,
            movesLeft = movesLeft,
            currentLevelData = currentLevelData,
            objectiveProgress = objectiveProgress,
            feedbackMessage = feedbackMessage,
            inventory = inventory,
            board = board,
            selectedTilePosition = selectedTilePos,
            tilesBeingMatched = tilesBeingMatched,
            isProcessing = isProcessing || !swapAnimationFinished, // Combină stările de blocare
            gameState = gameState,
            playerXP = playerXP, // Pasează XP-ul
            playerMoney = playerMoney,
            availableRecipesCount = availableRecipes.size, // Numărul de rețete
            swappingTilesInfo = swappingTiles,
            tile1AnimatedOffset = tile1Offset.value,
            tile2AnimatedOffset = tile2Offset.value,
            currentLevelId = currentLevelData?.levelId ?: 0, // Pasează ID-ul nivelului
            onShowShop = { showShopDialog = true },
            // Callback-uri
            onTileClick = { row, col -> // Logica de click e acum aici sau în swapTiles
                playSound(context, R.raw.click)
                val clickedPos = TilePosition(row, col)
                val currentSelection = selectedTilePos
                if (currentSelection == null) { selectedTilePos = clickedPos }
                else {
                    if (clickedPos == currentSelection) { selectedTilePos = null }
                    else if (areAdjacent(currentSelection, clickedPos)) {
                        // Inițiază doar animația (logica de swap e în LaunchedEffect)
                        if (swapAnimationFinished) { // Extra check
                            swappingTiles = Pair(currentSelection, clickedPos)
                            selectedTilePos = null
                            swapAnimationFinished = false
                            feedbackMessage = "Schimbare..."
                        }
                    } else { selectedTilePos = clickedPos }
                }
            },
            onShowRecipeBook = {  playSound(context, R.raw.click); showRecipeBookScreen = true }, // Modifică starea de navigare
            onMetaButtonClick = {  playSound(context, R.raw.click) },
            onRetryLevel = {
                playSound(context, R.raw.click)
                val currentIdx = currentLevelIndex
                currentLevelIndex = -1 // Index invalid temporar
                scope.launch {
                    delay(50)
                    currentLevelIndex = currentIdx
                }
            },
            onNextLevel = {
                playSound(context, R.raw.click)
                // Logica next level
                if (currentLevelIndex < gameLevels.size - 1) {
                    currentLevelIndex++
                } else {
                    gameState = "Finished"
                }
            }
        )
    }

    // --- Afișează dialogul PESTE orice ecran ---
    if (selectedRecipeToShow != null) {
        RecipeDetailDialog(
            recipe = selectedRecipeToShow!!,
            inventory = inventory,
            canCookChecker = ::canCookRecipe,
            onCook = ::cookRecipe, // Folosește funcția de gătit definită aici
            onDismiss = { playSound(context, R.raw.click); selectedRecipeToShow = null } // Închide dialogul
        )
    }

    // --- Afișare Dialog Shop ---
    if (showShopDialog) {
        ShopDialog(
            cookedMeals = cookedMealsInventory,
            recipesData = allPossibleRecipes, // Pasează lista completă pentru prețuri/nume
            onSellAll = {
                sellCookedMeals() // Apelează logica de vânzare
                showShopDialog = false // Închide dialogul după vânzare
            },
            onDismiss = { showShopDialog = false } // Închide dialogul la dismiss
        )
    }


}


// --- GameScreen Composable ---
@Composable
fun GameScreen(
    // Date de afișat
    score: Int,
    movesLeft: Int,
    currentLevelData: LevelData?,
    objectiveProgress: Map<LevelObjective, Int>,
    feedbackMessage: String,
    inventory: Map<Int, Int>,
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
    isProcessing: Boolean, // Indică dacă utilizatorul ar trebui blocat
    gameState: String,
    playerXP: Int,
    playerMoney: Int,
    availableRecipesCount: Int,
    swappingTilesInfo: Pair<TilePosition, TilePosition>?,
    tile1AnimatedOffset: IntOffset,
    tile2AnimatedOffset: IntOffset,
    // Callback-uri
    onTileClick: (row: Int, col: Int) -> Unit,
    onShowRecipeBook: () -> Unit,
    onMetaButtonClick: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    currentLevelId: Int, //  Primește ID-ul nivelului curent
    onShowShop: () -> Unit, // Callback pentru shop
) {
    val context = LocalContext.current // Obține context

    Column( // Containerul principal
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // Padding pentru barele sistemului
            .padding(16.dp), // Padding general
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --- Afișare Stare Joc (Victorie/Înfrângere) ---
        if (gameState == "Won") {
            Button(onClick = {
                playSound(context, R.raw.click) // Sunet click UI !!!
                onNextLevel()
            }) { Text("Nivelul Următor") }
        }
        else if (gameState == "Lost") {
            Button(onClick = {
                playSound(context, R.raw.click) // Sunet click UI !!!
                onRetryLevel()
            }) { Text("Reîncearcă Nivelul") }
        }

        // --- Rând Superior: Scor, XP, Rețete ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grup Scor
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scor:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(score.toString(), /* ... stil ... */) }
            // Grup XP
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("XP:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(playerXP.toString(), /* ... stil ... */) }
            // Grup Rețete (cu iconiță)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onShowRecipeBook() } // Aplică clickable AICI
            ) {
                Image(
                    painter = painterResource(id = R.drawable.carte), // Folosește ID-ul corect
                    contentDescription = "Rețete (${availableRecipesCount})", // Descriere mai bună
                    modifier = Modifier.size(40.dp), // Poate puțin mai mare?
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = availableRecipesCount.toString(),
                    style = MaterialTheme.typography.bodyLarge, // Ajustează stilul dacă vrei
                    fontWeight = FontWeight.Bold
                )
            }
            // --- Grup Shop (Iconiță Clickabilă) ---
            IconButton(onClick = { // Folosim IconButton pentru o zonă de click mai bună
                playSound(context, R.raw.click) // Sunet UI
                onShowShop() // Deschide dialogul Shop
            }) {
                Image( // Sau Icon dacă preferi și ai setat tint/size
                    painter = painterResource(id = R.drawable.market),
                    contentDescription = "Magazin (Vinde Produse)",
                    modifier = Modifier.size(32.dp) // Ajustează dimensiunea
                    // Nu folosi tint dacă e PNG colorat
                )
            }



            Row(verticalAlignment = Alignment.CenterVertically) {
                 Image(
                     painterResource(id = R.drawable.coin),
                     contentDescription = "Bani",
                     modifier = Modifier.size(20.dp),
                 )
                 Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = playerMoney.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Rând Info Nivel: Mutări și Obiective Compacte ---
        if (currentLevelData != null && gameState == "Playing") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Nume Nivel
                Text(
                    text = "Nivel ${currentLevelData.levelId}: ${currentLevelData.name}",
                    style = MaterialTheme.typography.titleSmall, // Font mai mic
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Rând pentru Mutări și Obiective Principale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Sau SpaceBetween
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mutări Rămase
                    Text(
                        text = "Mutări: $movesLeft",
                        style = MaterialTheme.typography.bodyMedium, // Font normal
                        fontWeight = FontWeight.Bold,
                        color = if (movesLeft <= 5 && movesLeft > 0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )

                    // --- Afișare Obiective MAI COMPACTĂ ---
                    // Afișăm doar 1-2 obiective principale sau folosim iconițe
                    // Exemplu: Afișează doar PRIMUL obiectiv neîndeplinit
                    val firstUnmetObjective = currentLevelData.objectives.firstOrNull { (objectiveProgress[it] ?: 0) < it.targetQuantity }
                    if (firstUnmetObjective != null) {
                        val progress = objectiveProgress[firstUnmetObjective] ?: 0
                        val objectiveText = formatObjective(firstUnmetObjective, progress, score) // Folosim o funcție helper
                        Text(
                            text = "🎯 $objectiveText", // Folosim emoji sau iconiță
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp // Font puțin mai mic
                        )
                    } else if (currentLevelData.objectives.isNotEmpty()) {
                        // Toate obiectivele sunt îndeplinite (dar jocul nu s-a terminat încă?)
                        Text("✅ Obiective OK!", fontSize = 13.sp, color = Color.Gray)
                    }
                    // TODO: Poți adăuga un mic indicator dacă sunt MAI MULTE obiective
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Rând Butoane (Meta & Carte Bucate) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onMetaButtonClick, enabled = false, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { Text("Îmbunătățiri curand") } // Text mai scurt
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Mesaj Feedback (poate font mai mic?) ---
        Text(text = feedbackMessage, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp), fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // --- Afișaj Inventar (poate mai compact?) ---
        Text("Inventar:", style = MaterialTheme.typography.labelLarge) // Font mai mic
        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal=4.dp), horizontalArrangement = Arrangement.Center) { /* ... cod inventar (poate cu size=24.dp la Image) ... */ }
        Spacer(modifier = Modifier.height(10.dp))


        // Afișează butonul pentru piata
        val shouldShowShopButton = currentLevelId == 1
        // Sau poți folosi gameState == "Won" și o condiție pe levelId
        if (shouldShowShopButton && gameState == "Playing") { // Sau la final de nivel? Momentan în timpul jocului
            Spacer(modifier = Modifier.height(10.dp))

        }






        // --- Tabla de Joc ---
        // Folosim weight pentru a împinge tabla în jos, DAR nu prea mult
        Box(modifier = Modifier.weight(0.8f)) { // Încearcă diferite valori < 1f
            if (gameState == "Playing" || gameState == "Won" || gameState == "Lost") {
                GameBoard(
                    board = board,
                    selectedTilePosition = selectedTilePosition,
                    tilesBeingMatched = tilesBeingMatched,
                    swappingTilesInfo = swappingTilesInfo,
                    tile1AnimatedOffset = tile1AnimatedOffset,
                    tile2AnimatedOffset = tile2AnimatedOffset,
                    onTileClick = { row, col ->
                        if (gameState == "Playing" && !isProcessing) { // Permite click doar dacă se joacă și nu se procesează
                            playSound(context, R.raw.click) // Sunet click piesă !!!
                            onTileClick(row, col)
                        }
                    }
                )
            } else { /* Spacer sau mesaj "Joc Terminat" */ }
        }

        // Spacer final mic
        Spacer(modifier = Modifier.height(8.dp))
    }
}


// --- Funcție Helper pentru Formatare Obiectiv (la nivel de fișier sau în App) ---
fun formatObjective(objective: LevelObjective, progress: Int, currentScore: Int): String {
    val target = objective.targetQuantity
    val currentProgress = when (objective.type) {
        ObjectiveType.REACH_SCORE -> currentScore.coerceAtMost(target) // Folosim scorul curent
        else -> progress // Folosim progresul stocat pentru colectare/gătit
    }.coerceAtMost(target) // Asigurăm că nu depășește ținta

    return when (objective.type) {
        ObjectiveType.COLLECT_INGREDIENTS -> "${getIngredientName(objective.targetId)}: $currentProgress/$target"
        ObjectiveType.COOK_RECIPES -> {
            val recipeName = allPossibleRecipes.find { it.id == objective.targetId }?.name ?: "Rețetă ${objective.targetId}"
            "$recipeName: $currentProgress/$target"
        }
        ObjectiveType.REACH_SCORE -> "Scor: $currentProgress/$target"
    }
}


// --- RecipeBookScreen Composable (Simplificat - primește totul ca parametri) ---
@Composable
fun RecipeBookScreen(
    recipes: List<Recipe>,
    inventory: Map<Int, Int>,
    canCookChecker: (Recipe) -> Boolean,
    onCookRecipe: (Recipe) -> Unit, // Pasează funcția de gătit reală
    onShowRecipeDetails: (Recipe) -> Unit, // Pentru a deschide dialogul
    onClose: () -> Unit
) {
    // NU mai are nevoie de stare locală pentru dialog
    // var recipeForDialog by remember { mutableStateOf<Recipe?>(null) }
    val context = LocalContext.current
    Column(  modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(16.dp) ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                playSound(context, R.raw.click) // Sunet UI
                onClose()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cartea Mea de Bucate", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recipes, key = { it.id }) { recipe ->
                val canCook = remember(inventory, recipe) { canCookChecker(recipe) }
                Column(
                    modifier = Modifier.clickable {
                        playSound(context, R.raw.click) // Sunet UI
                        onShowRecipeDetails(recipe)
                    }
                        .fillMaxWidth()
                        .clickable { onShowRecipeDetails(recipe) } // Apelează callback-ul pentru dialog
                        .padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) { /* ... Nume + Indicator Gata/Lipsă ... */ }
                    Text(recipe.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Divider()
            }
            if (recipes.isEmpty()){ /* ... item listă goală ... */ }
        }
    }
    // Dialogul NU mai este afișat de AICI, ci din Match3GameApp
}


// --- RecipeDetailDialog Composable (Rămâne la fel) ---
// --- Composable pentru Dialogul Detalii Rețetă ---
@Composable
fun RecipeDetailDialog(
    recipe: Recipe, // Rețeta de afișat
    inventory: Map<Int, Int>, // Inventarul curent pentru verificare
    canCookChecker: (Recipe) -> Boolean, // Funcția care verifică dacă se poate găti
    onCook: (Recipe) -> Unit, // Funcția de apelat la apăsarea "Gătește"
    onDismiss: () -> Unit // Funcția de apelat la închiderea dialogului
) {
    val canCookCurrentRecipe = remember(inventory, recipe) { canCookChecker(recipe) } // Verifică dacă se poate găti
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, // Apelează funcția primită
        title = { Text(text = recipe.name) },
        text = {
            Column {
                Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ingrediente Necesare:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        val drawableResId = tileDrawables[ingredientId]
                        if (drawableResId != null) { /* ... Image ... */ }
                        else { /* ... Box fallback ... */ }
                        Spacer(modifier = Modifier.width(8.dp))
                        val quantityOwned = inventory.getOrDefault(ingredientId, 0)
                        Text("${getIngredientName(ingredientId)}: $quantityOwned / $quantityNeeded")
                        if (quantityOwned < quantityNeeded) {
                            Text(" (Lipsă!)", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        onCook(recipe) // Apelează funcția primită
                    },
                    enabled = canCookCurrentRecipe
                ) {
                    Text("Gătește")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { // Apelează funcția primită
                    Text("Înapoi")
                }
            }
        }
    )
}

// --- GameBoard Composable ---
@Composable
fun GameBoard(
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
    swappingTilesInfo: Pair<TilePosition, TilePosition>?, // Perechea care face swap
    tile1AnimatedOffset: IntOffset, // Offset animat calculat pentru piesa 1
    tile2AnimatedOffset: IntOffset, // Offset animat calculat pentru piesa 2
    onTileClick: (row: Int, col: Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFA0A0A0))
            .padding(4.dp)
    ) {
        val tileSize = maxWidth / COLS
        Column {
            board.forEachIndexed { rowIndex, rowData ->
                Row {
                    rowData.forEachIndexed { colIndex, tileType ->
                        val currentPos = TilePosition(rowIndex, colIndex)
                        val isSelected = currentPos == selectedTilePosition
                        val isDisappearing = tilesBeingMatched.contains(currentPos)
                        val animatedOffset = when (currentPos) {
                            swappingTilesInfo?.first -> tile1AnimatedOffset
                            swappingTilesInfo?.second -> tile2AnimatedOffset
                            else -> IntOffset.Zero // Fără offset dacă nu face swap
                        }

                        if (tileType != EMPTY_TILE) {
                            GameTile(
                                type = tileType,       // Pasează tipul piesei curente
                                size = tileSize,       // Pasează dimensiunea calculată a piesei
                                isSelected = isSelected,
                                isDisappearing = isDisappearing,
                                animatedOffset = animatedOffset,
                                onClick = { onTileClick(rowIndex, colIndex) }
                            )
                        } else { /* ... Spacer ... */ }
                    }
                }
            }
        }
    }
}

// --- GameTile Composable ---
@Composable
fun GameTile(
    type: Int, // Poate fi negativ!
    size: Dp,
    isSelected: Boolean,
    isDisappearing: Boolean,
    animatedOffset: IntOffset, // Pentru swap
    onClick: () -> Unit
) {
    // --- Stare animație dispariție (scale, alpha) ---
    val disappearingScale = remember { Animatable(1f) }
    val disappearingAlpha = remember { Animatable(1f) }
    // --- Stare animație CĂDERE ---
    val fallTranslationY = remember { Animatable(0f) } // Offset Y inițial 0
    val fallAlpha = remember { Animatable(1f) }       // Alpha inițial 1

    // Determină tipul real și dacă e piesă nouă
    val actualType = abs(type)
    val isNewTile = type < 0

    // --- Animație Dispariție ---
    LaunchedEffect(isDisappearing) {
        if (isDisappearing) {
            // Lansează animațiile în paralel
            launch {
                disappearingScale.animateTo(
                    targetValue = 0.3f,
                    animationSpec = tween(durationMillis = 300)
                )
            }
            launch {
                disappearingAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300)
                )
            }
        } else {
            // Resetare instantanee dacă nu (mai) dispare
            // Asigură că piesele care NU dispar sunt vizibile/la scala normală
            if (disappearingScale.value != 1f) disappearingScale.snapTo(1f)
            if (disappearingAlpha.value != 1f) disappearingAlpha.snapTo(1f)
        }
    }

    // --- Animație Cădere ---
    LaunchedEffect(isNewTile, type) { // Adăugăm și 'type' ca cheie pentru resetare corectă
        if (isNewTile) {
            // Stare inițială (deasupra și invizibilă)
            fallTranslationY.snapTo(-size.value * 2) // Începe de sus
            fallAlpha.snapTo(0f) // Complet transparent
            // Animație spre poziția finală
            launch {
                fallTranslationY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300, delayMillis = 50)
                )
            }
            launch {
                fallAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 300, delayMillis = 50)
                )
            }
        } else {
            // Resetare instantanee dacă piesa NU este nouă
            // (important dacă o piesă existentă cade și nu trebuie să refacă animația de apariție)
            if (fallTranslationY.value != 0f) fallTranslationY.snapTo(0f)
            if (fallAlpha.value != 1f) fallAlpha.snapTo(1f)
        }
    }

    // --- Modificator selecție ---
    val selectionModifier = if (isSelected) {
        Modifier
            .border(
                width = 2.dp,
                color = Color.Yellow,
                shape = MaterialTheme.shapes.small
            )
            .scale(1.05f)
    } else {
        Modifier
    }

    // Obține drawable pentru tipul real
    val drawableResId = tileDrawables[actualType]

    Box(
        modifier = Modifier
            // Aplică offset-ul de la swap
            .offset { animatedOffset }
            // --- *MODIFICAT* Aplică transformările grafice folosind "this." ---
            .graphicsLayer {
                scaleX = disappearingScale.value
                scaleY = disappearingScale.value
                // Combină alpha-urile și setează proprietatea scope-ului
                this.alpha = disappearingAlpha.value * fallAlpha.value
                translationY = fallTranslationY.value
            }
            // --- Restul modificatorilor ---
            .size(size)
            .padding(1.dp)
            .then(selectionModifier) // Aplică selecția după transformări
            .background(
                color = tileColors[actualType]?.copy(alpha = 0.4f) ?: Color.Gray.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Afișează imaginea
        if (drawableResId != null) {
            Image(
                painter = painterResource(id = drawableResId),
                contentDescription = getIngredientName(actualType),
                modifier = Modifier.fillMaxSize(0.8f)
            )
        }
    }
}




// --- Composable pentru Dialogul Shop ---
@Composable
fun ShopDialog(
    cookedMeals: Map<Int, Int>, // Inventarul de mâncare gătită <RecipeId, Quantity>
    recipesData: List<Recipe>, // Avem nevoie de datele rețetelor pentru nume și preț
    onSellAll: () -> Unit, // Funcția de apelat la apăsarea "Vinde Tot"
    onDismiss: () -> Unit // Funcția de apelat la închiderea dialogului
) {
    // Calculează valoarea totală a mărfii (opțional, pentru afișare)
    var totalValue = 0
    cookedMeals.forEach { (recipeId, quantity) ->
        val recipe = recipesData.find { it.id == recipeId }
        if (recipe != null) {
            totalValue += recipe.sellingPrice * quantity
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Piața Locală (Magazin)") },
        text = {
            Column {
                if (cookedMeals.isEmpty()) {
                    Text("Nu ai pregătit nimic de vânzare încă!")
                } else {
                    Text("Produse gata de vânzare:")
                    Spacer(modifier = Modifier.height(8.dp))
                    // Listează produsele gătite
                    LazyColumn { // Folosim LazyColumn dacă lista poate fi lungă
                        items(cookedMeals.entries.toList(), key = { it.key }) { (recipeId, quantity) ->
                            val recipe = recipesData.find { it.id == recipeId }
                            if (recipe != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Iconiță mică a ingredientului principal (sau a rețetei?) - Simplificare: folosim primul ingredient
                                        val firstIngredientId = recipe.ingredientsNeeded.keys.firstOrNull()
                                        val drawableResId = if(firstIngredientId != null) tileDrawables[firstIngredientId] else null
                                        if (drawableResId != null) {
                                            Image(painterResource(id = drawableResId), contentDescription = null, modifier = Modifier.size(24.dp))
                                        } else {
                                            Box(Modifier.size(24.dp)) // Placeholder
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${recipe.name} x $quantity")
                                    }
                                    Text("+${recipe.sellingPrice * quantity} Bani", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Valoare Totală: $totalValue Bani", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = onSellAll,
                    enabled = cookedMeals.isNotEmpty() // Activează doar dacă e ceva de vândut
                ) {
                    Text("Vinde Tot")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Închide")
                }
            }
        }
    )
}

// --- Preview-uri (Poți crea preview-uri separate pentru GameScreen și RecipeBookScreen dacă vrei) ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        Match3GameApp()
    }
}
