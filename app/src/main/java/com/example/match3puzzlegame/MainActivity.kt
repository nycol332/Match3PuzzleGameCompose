// ATENȚIE: Asigură-te că acest nume de pachet se potrivește cu structura proiectului tău!
package com.example.match3puzzlegame
import kotlinx.coroutines.delay // Pentru pauze
import kotlinx.coroutines.launch // Pentru a porni corutina
import android.os.Bundle
import androidx.compose.foundation.shape.CircleShape
import android.util.Log // Pentru depanare
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.* // Include remember, mutableStateOf, Composable etc.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale // Import pentru scalare
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.match3puzzlegame.ui.theme.Match3PuzzleGameTheme
import kotlin.math.abs
import androidx.compose.animation.core.Animatable // Pentru animație detaliată
import androidx.compose.animation.core.tween // Specifică durata animației
import androidx.compose.ui.graphics.graphicsLayer // Pentru a aplica scale și alpha
import androidx.compose.ui.res.painterResource // Pentru a încărca drawable
import androidx.compose.foundation.Image // Pentru a afișa imagini
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton // Pentru butonul de închidere al dialogului
import androidx.compose.foundation.shape.CircleShape // Importat deja, dar verifică
import androidx.compose.foundation.Image // Importat deja, dar verifică
import androidx.compose.ui.res.painterResource // Importat deja, dar verifică
import androidx.compose.animation.core.VectorConverter // Pentru IntOffset
import androidx.compose.animation.core.tween // Deja importat? Verifică.
import androidx.compose.ui.unit.IntOffset // Pentru Modifier.offset
import androidx.compose.ui.graphics.graphicsLayer // Import pentru translationY și alpha


// --- Constante ---
const val ROWS = 8
const val COLS = 8
const val META_COST = 100
const val EMPTY_TILE = 0
const val TILE_TYPE_1 = 1
const val TILE_TYPE_2 = 2
const val TILE_TYPE_3 = 3
const val TILE_TYPE_4 = 4
const val TILE_TYPE_5 = 5


val TILE_TYPES = listOf(TILE_TYPE_1, TILE_TYPE_2, TILE_TYPE_3, TILE_TYPE_4, TILE_TYPE_5)

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


data class Recipe(
    val id: Int, // Identificator unic
    val name: String,
    val description: String,
    val ingredientsNeeded: Map<Int, Int>
)


val initialRecipes = listOf(
    Recipe(
        id = 1,
        name = "Gustare misterioasa",
        description = "Cucumber + Corn",
        ingredientsNeeded = mapOf(TILE_TYPE_1 to 5, TILE_TYPE_4 to 3) // 5 Roșii, 3 Mere (exemplu!)
    ),
    Recipe(
        id = 2,
        name = "Salata misterioasa",
        description = "Tomato + Corn",
        ingredientsNeeded = mapOf(TILE_TYPE_2 to 4, TILE_TYPE_4 to 4) // 4 Portocale, 4 Mere
    ),
    Recipe(
        id = 3,
        name = "Tocăniță Misterioasă",
        description = "Cucumber + Potato + Onion",
        ingredientsNeeded = mapOf(TILE_TYPE_1 to 3, TILE_TYPE_5 to 6, TILE_TYPE_3 to 2) // 3 Roșii, 6 Vinete, 2 Afine
    )
)



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


// --- Clasa pentru Poziție ---
data class TilePosition(val row: Int, val col: Int)

// --- TAG pentru Logcat ---
private const val TAG = "Match3Game"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Match3PuzzleGameTheme {
                GameScreen()
            }
        }
    }
}


// --- Structuri pentru Nivele și Obiective ---

// Tipuri posibile de obiective
enum class ObjectiveType {
    COLLECT_INGREDIENTS, // Colectează un număr specific dintr-un ingredient
    COOK_RECIPES,       // Gătește o rețetă specifică de un număr de ori
    REACH_SCORE         // Atinge un anumit scor
    // TODO: Adaugă alte tipuri (ex: CLEAR_BLOCKERS - curăță piese speciale)
}

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
    val maxMoves: Int
)

// --- Date Nivele Inițiale ---
val gameLevels = listOf(
    LevelData(
        levelId = 1,
        name = "Piața Locală - Începuturi",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_2, 15) // Colectează 15 Roșii
        ),
        maxMoves = 20
    ),
    LevelData(
        levelId = 2,
        name = "Prima Comandă - Salata",
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 1, 1) // Gătește Salata Proaspătă (ID 1) o dată
        ),
        maxMoves = 25
    ),
    LevelData(
        levelId = 3,
        name = "Provizia de Iarnă",
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_5, 20), // 20 Cartofi
            LevelObjective(ObjectiveType.REACH_SCORE, 0, 5000) // Atinge 5000 puncte
        ),
        maxMoves = 30
    )
    // Adaugă mai multe nivele
)




@Composable
fun GameScreen() {

    var inventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    var feedbackMessage by remember { mutableStateOf("") }

    var selectedTilePos by remember { mutableStateOf<TilePosition?>(null) }


    // --- Stare pentru animația de SWAP ---
    // Reține perechea de poziții care fac swap și offset-urile lor animate
    var swappingTiles by remember { mutableStateOf<Pair<TilePosition, TilePosition>?>(null) }
    // Offset animat pentru prima piesă din pereche
    val tile1Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    // Offset animat pentru a doua piesă din pereche
    val tile2Offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    // Stare pentru a ști când animația de swap este gata
    var swapAnimationFinished by remember { mutableStateOf(true) } // Inițial, nicio animație nu rulează


    var tilesBeingMatched by remember { mutableStateOf<Set<TilePosition>>(emptySet()) }

    var isProcessing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val isMetaButtonEnabled = false

    val metaButtonText = "Îmbunătățiri (în curând)"

    var score by remember { mutableStateOf(0) }

    val availableRecipes by remember { mutableStateOf(initialRecipes) } // Lista rețetelor
    var selectedRecipeToShow by remember { mutableStateOf<Recipe?>(null) }


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


    // --- Funcție Helper pentru Adiacență ---
    fun areAdjacent(pos1: TilePosition, pos2: TilePosition): Boolean {
        val rowDiff = abs(pos1.row - pos2.row)
        val colDiff = abs(pos1.col - pos2.col)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
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


    var board by remember {
        mutableStateOf(generateValidInitialBoard())
    }


    // --- Starea Nivelului ---
    var currentLevelIndex by remember { mutableStateOf(0) } // Începe cu primul nivel (index 0)
    val currentLevelData =
        remember(currentLevelIndex) { gameLevels.getOrNull(currentLevelIndex) } // Obține datele nivelului curent
    var gameState by remember { mutableStateOf("Playing") } // Sau un Enum: GameState.Playing

    // Starea specifică a nivelului curent
    var movesLeft by remember { mutableStateOf(currentLevelData?.maxMoves ?: 0) }

    // Stocăm progresul obiectivelor într-un map (Objective -> Progress)
    var objectiveProgress by remember { mutableStateOf<Map<LevelObjective, Int>>(emptyMap()) }
    // Starea jocului (Playing, Won, Lost)

    fun checkLevelEndCondition() {
        if (gameState != "Playing") return // Nu verifica dacă jocul s-a terminat deja

        if (currentLevelData == null) {
            Log.w(TAG, "checkLevelEndCondition called with null level data!")
            return
        }

        // Verifică dacă TOATE obiectivele sunt îndeplinite
        val allObjectivesMet = currentLevelData.objectives.all { objective ->
            val progress = objectiveProgress[objective] ?: 0
            progress >= objective.targetQuantity
        }

        if (allObjectivesMet) {
            // --- CONDIȚIE DE VICTORIE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} WON!")
            gameState = "Won"
            feedbackMessage = "Nivel ${currentLevelData.levelId} Terminat!"
            // TODO: Afișează un dialog/ecran de victorie
            // TODO: Pregătește trecerea la nivelul următor (ex: incrementează currentLevelIndex după o acțiune a userului)
        } else if (movesLeft <= 0) {
            // --- CONDIȚIE DE ÎNFRÂNGERE ---
            Log.i(TAG, "Level ${currentLevelData.levelId} LOST! No moves left.")
            gameState = "Lost"
            feedbackMessage = "Ai rămas fără mutări! Reîncearcă!"
            // TODO: Afișează un dialog/ecran de înfrângere cu opțiune de Retry
        } else {
            // --- Nivelul Continuă ---
            Log.d(TAG, "Level continues. Moves left: $movesLeft. Objectives met: $allObjectivesMet")
        }
    }
    // --- Sfârșit checkLevelEndCondition ---


    // --- Resetare la începutul nivelului (când currentLevelIndex se schimbă) --- *NOU*
    LaunchedEffect(currentLevelData) {
        Log.d(TAG, "--- LaunchedEffect triggered for level: ${currentLevelData?.levelId} ---") // LOG NOU
        val levelData = gameLevels.getOrNull(currentLevelIndex) // Obține datele aici
        if (levelData != null) {
            Log.d(TAG, "Resetting state for Level ${levelData.levelId}")
            // Resetează starea pentru noul nivel
            board = generateValidInitialBoard()
            movesLeft = levelData.maxMoves // Folosește levelData obținut local
            Log.d(TAG, "movesLeft reset to: ${levelData.maxMoves}")
            objectiveProgress = levelData.objectives.associateWith { 0 }
            // ... restul resetărilor ...
            gameState = "Playing"
        } else {
            Log.e(TAG, "Invalid level index: $currentLevelIndex")
            feedbackMessage = "Felicitări, ai terminat toate nivelele!"
            gameState = "Finished"
        }
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
        Log.d(TAG, "processMatchesAndCascades finished.")
    }


    // ---  Funcție pentru Acțiunea de Gătit ---
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
            // Opcional: elimină intrarea dacă ajunge la 0
            // if (updatedInventory[ingredientId] == 0) {
            //     updatedInventory.remove(ingredientId)
            // }
        }
        inventory = updatedInventory // Actualizează starea inventarului

        // --- *NOU* Actualizează progresul pentru obiectivele de gătit ---
        val updatedProgress = objectiveProgress.toMutableMap()
        currentLevelData?.objectives?.forEach { objective ->
            if (objective.type == ObjectiveType.COOK_RECIPES && objective.targetId == recipe.id) {
                val currentProg = updatedProgress[objective] ?: 0
                // Incrementează progresul, limitat la țintă
                updatedProgress[objective] =
                    (currentProg + 1).coerceAtMost(objective.targetQuantity)
                Log.d(
                    TAG,
                    "Cook objective progress for ${recipe.name}: ${updatedProgress[objective]}/${objective.targetQuantity}"
                )
            }
        }
        objectiveProgress = updatedProgress // Aplică actualizările

        feedbackMessage = "Ai gătit ${recipe.name} cu succes! Delicios!"
        selectedRecipeToShow = null

        // ---  Verifică finalul nivelului DUPĂ gătit ---
        checkLevelEndCondition()

        // TODO: Adaugă aici recompense reale (XP, monedă, etc.)
    }



    // --- Funcție Helper pentru Swap ---
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

    // --- Efect pentru a rula animația de SWAP (Varianta Corectată cu Job.join()) ---
    LaunchedEffect(swappingTiles) {
        val tiles = swappingTiles
        if (tiles != null) {
            Log.d(TAG, "LaunchedEffect: Animating swap for $tiles")
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

            // --- Așteaptă ca AMBELE animații să se termine --- *MODIFICAT*
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
    // --- Sfârșit LaunchedEffect Animație Swap ---

    val currentRecipe = selectedRecipeToShow // Copie locală pentru dialog
    if (currentRecipe != null) {
        val canCookCurrentRecipe = remember(inventory, currentRecipe) { canCookRecipe(currentRecipe) }
        AlertDialog(
            onDismissRequest = {
                // Ce se întâmplă când utilizatorul dă click în afara dialogului sau apasă Back
                selectedRecipeToShow = null // Închide dialogul
                Log.d(TAG, "Recipe dialog dismissed.")
            },
            title = { Text(text = currentRecipe.name) }, // Titlul dialogului
            text = { // Conținutul principal al dialogului
                Column {
                    Text(currentRecipe.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ingrediente Necesare:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    // Listează ingredientele
                    currentRecipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            val drawableResId = tileDrawables[ingredientId]
                            if (drawableResId != null) {
                                Image(
                                    painter = painterResource(id = drawableResId),
                                    contentDescription = getIngredientName(ingredientId),
                                    modifier = Modifier.size(24.dp) // Iconiță mică
                                )
                            } else {
                                // Fallback cerc colorat
                                Box(Modifier.size(20.dp).background(tileColors[ingredientId] ?: Color.Gray, CircleShape))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val quantityOwned = inventory.getOrDefault(ingredientId, 0)
                            Text("${getIngredientName(ingredientId)}: $quantityOwned / $quantityNeeded")

                            if (quantityOwned < quantityNeeded) {
                                Text(" (Lipsă!)", color = Color.Red, fontSize = 10.sp)
                            }
                        }
                    }
                    // TODO (Mai târziu): Adaugă buton de "Gătește" dacă ai suficiente ingrediente
                }
            },
            confirmButton = { // Butonul principal (aici doar pentru a închide)
                Row { // Folosim Row pentru a avea două butoane
                    TextButton(
                        onClick = { cookRecipe(currentRecipe) },
                        enabled = canCookCurrentRecipe // Activează doar dacă se poate găti
                    ) {
                        Text("Gătește")
                    }
                    Spacer(Modifier.width(8.dp)) // Spațiu între butoane
                    TextButton(onClick = { selectedRecipeToShow = null }) {
                        Text("Înapoi") // Am redenumit butonul OK
                    }
                }
            }
            // Poți adăuga și un dismissButton dacă vrei
            // dismissButton = { TextButton(onClick = { selectedRecipeToShow = null }) { Text("Anulează") } }
        )
    }



    // --- Structura UI ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (gameState == "Won") {
            Text("NIVEL TERMINAT!", color = Color.Green, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            // TODO: Adaugă buton "Nivelul Următor"
            Button(onClick = {
                // Incrementează indexul pentru a trece la nivelul următor
                if (currentLevelIndex < gameLevels.size - 1) {
                    currentLevelIndex++
                } else {
                    // TODO: Gestionează finalul jocului
                    feedbackMessage = "Felicitări! Ai terminat toate nivelele!"
                    gameState = "Finished" // Stare finală
                }
            }) { Text("Nivelul Următor") }

        } else if (gameState == "Lost") {
            Text("FĂRĂ MUTĂRI!", color = Color.Red, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            // TODO: Adaugă buton "Reîncearcă"
            Button(onClick = {
                // Resetează nivelul curent prin simpla re-trigerare a LaunchedEffect
                // O metodă e să forțezi schimbarea indexului și revenirea la el,
                // dar mai curat e să avem o funcție dedicată de reset.
                // Soluție simplă: folosim un key diferit în LaunchedEffect
                // Dar pentru început, putem doar reseta manual stările necesare
                // sau, și mai simplu, forțăm recrearea prin schimbarea indexului temporar
                val currentIdx = currentLevelIndex
                currentLevelIndex = -1 // Index invalid temporar
                // Delay mic pentru a permite recompoziția
                scope.launch {
                    delay(50)
                    currentLevelIndex = currentIdx // Revine la indexul curent, triggerează LaunchedEffect
                }
            }) { Text("Reîncearcă Nivelul") }
        }

//        Row(verticalAlignment = Alignment.CenterVertically) {
//            Text(text = "Scor:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
//            Spacer(modifier = Modifier.width(8.dp))
//            Text(
//                text = score.toString(), // Afișează scorul din starea 'score'
//                fontSize = 20.sp,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.primary // Folosește o culoare din tema
//            )
//        }
//        Spacer(modifier = Modifier.height(10.dp))

        // --- Secțiunea Info Nivel ---
        if (currentLevelData != null && gameState == "Playing") { // Afișează doar dacă avem date și jucăm
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Numele Nivelului
                Text(
                    text = "Nivel ${currentLevelData.levelId}: ${currentLevelData.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Mutări Rămase
                Text(
                    text = "Mutări: $movesLeft",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (movesLeft <= 5) Color.Red else MaterialTheme.colorScheme.onSurface // Roșu când sunt puține mutări
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Afișare Obiective
                Text("Obiective:", style = MaterialTheme.typography.bodyMedium)
                currentLevelData.objectives.forEach { objective ->
                    val progress = objectiveProgress[objective] ?: 0 // Progresul curent
                    val objectiveText = when (objective.type) {
                        ObjectiveType.COLLECT_INGREDIENTS ->
                            "${getIngredientName(objective.targetId)}: $progress / ${objective.targetQuantity}"
                        ObjectiveType.COOK_RECIPES -> {
                            // Găsește numele rețetei după ID
                            val recipeName = availableRecipes.find { it.id == objective.targetId }?.name ?: "Rețetă ${objective.targetId}"
                            "Gătește $recipeName: $progress / ${objective.targetQuantity}"
                        }
                        ObjectiveType.REACH_SCORE ->
                            "Scor: ${score.coerceAtMost(objective.targetQuantity)} / ${objective.targetQuantity}" // Afișează scorul curent vs țintă
                    }
                    // Afișează textul obiectivului, poate cu o bifă dacă e complet
                    val isCompleted = progress >= objective.targetQuantity
                    Text(
                        text = if (isCompleted) "✅ $objectiveText" else "➡️ $objectiveText",
                        fontSize = 14.sp,
                        color = if (isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface // Gri dacă e completat
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp)) // Spațiu sub obiective
        }// --- Sfârșit Secțiune Info Nivel ---




        // --- Buton Meta  ---
        Button(
            onClick = {
                if (isMetaButtonEnabled) {
                    feedbackMessage = "Ai cheltuit $META_COST stele! 🎉"
                }
                Log.d(TAG, "Meta Button Clicked (currently disabled)")
            },
            enabled = isMetaButtonEnabled
        ) {
            Text(text = metaButtonText)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Mesaj Feedback (rămâne la fel) ---
        Text(
            text = feedbackMessage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (feedbackMessage.startsWith("Ai cheltuit")) Color.Magenta else Color.Green,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- Inventar
        Text("Inventar:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly // Distribuie spațiul
        ) {
            inventory.entries.sortedBy { it.key }.forEach { (ingredientId, quantity) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Afișează un indicator vizual (culoarea piesei)
                    val drawableResId = tileDrawables[ingredientId] // Găsește ID-ul resursei
                    if (drawableResId != null) {
                        Image(
                            painter = painterResource(id = drawableResId),
                            contentDescription = getIngredientName(ingredientId), // Text alternativ
                            modifier = Modifier.size(32.dp) // Ajustează dimensiunea după preferințe
                        )
                    } else {
                        // Fallback: Afișează un cerc colorat dacă imaginea nu e găsită
                        Box(modifier = Modifier
                            .size(24.dp)
                            .background(tileColors[ingredientId] ?: Color.Gray, CircleShape)
                        )
                    }
                    // Afișează cantitatea
                    Text(
                        text = quantity.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Opcional: Afișează numele ingredientului
                    Text(
                        text = getIngredientName(ingredientId),
                        fontSize = 10.sp
                    )
                }
            }
            // Afișează un mesaj dacă inventarul e gol
            if (inventory.isEmpty()) {
                Text("Colectează ingrediente potrivind piese!", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))


        // --- Secțiunea Rețete (Listă Nume) --- *MODIFICAT* - Mutăm lista într-un loc mai bun
        // Momentan o comentăm/ștergem de aici pentru a nu aglomera ecranul principal
        /*
        Text("Rețete Descoperite:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
             availableRecipes.forEach { recipe -> /* ... Text clickabil ... */ }
             if (availableRecipes.isEmpty()) { /* ... mesaj gol ... */ }
        }
        Spacer(modifier = Modifier.height(16.dp))
        */



        // --- Buton pentru a deschide lista de rețete ---
        Button(onClick = {
            // TODO: Navighează la un ecran dedicat pentru rețete sau afișează lista altfel
            // Momentan, putem folosi feedback-ul sau un log
            feedbackMessage = "Vezi cartea de bucate! (TODO)"
            Log.d(TAG, "Recipe book button clicked - TODO: Show list properly")
            // Sau, temporar, pentru test, putem afișa prima rețetă direct:
            if (availableRecipes.isNotEmpty()) { selectedRecipeToShow = availableRecipes.first() }
        }) {
            Text("Carte de Bucate")
        }
        Spacer(modifier = Modifier.height(16.dp)) // Spațiu înainte de tablă




        // --- Tabla de Joc ---
        if (gameState == "Playing") {
            Spacer(modifier = Modifier.height(16.dp))
            GameBoard(
                board = board,
                selectedTilePosition = selectedTilePos,
                swappingTilesInfo = swappingTiles,
                tile1AnimatedOffset = tile1Offset.value,
                tile2AnimatedOffset = tile2Offset.value,
                tilesBeingMatched = tilesBeingMatched,
                onTileClick = { row, col ->
                    if (isProcessing || !swapAnimationFinished) { // *MODIFICAT* Blochează click și în timpul animației de swap
                        Log.d(TAG, "Click ignorat - procesare sau animație swap în curs")
                        return@GameBoard
                    }

                    val clickedPos = TilePosition(row, col)
                    Log.d(TAG, "onTileClick: ($row, $col)")

                    val currentSelection = selectedTilePos // Copie locală

                    if (currentSelection == null) {
                        // Prima Selecție
                        selectedTilePos = clickedPos
                        feedbackMessage = "Selectat: (${clickedPos.row}, ${clickedPos.col})"
                        Log.d(TAG, "Prima selecție: $clickedPos")
                    } else {
                        // A Doua Selecție
                        if (clickedPos == currentSelection) {
                            // Click pe aceeași piesă: Deselectare
                            selectedTilePos = null
                            feedbackMessage = "Deselectat"
                            Log.d(TAG, "Deselectare")
                        } else if (areAdjacent(currentSelection, clickedPos)) {
                            // Click pe piesă adiacentă: Swap
                            Log.d(TAG, "Initiating swap animation between $currentSelection and $clickedPos")
                            swappingTiles = Pair(currentSelection, clickedPos) // Setează perechea care face swap
                            selectedTilePos = null // Deselectează vizual (fără highlight galben)
                            swapAnimationFinished = false // Marcheză că animația începe
                            feedbackMessage = "Schimbare..." // Feedback temporar

                            // Animația va fi lansată de un LaunchedEffect care observă `swappingTiles`
                        } else {
                            // --- Selectare piesă neadiacentă (rămâne la fel) ---
                            selectedTilePos = clickedPos
                            feedbackMessage = "Selectat: (${clickedPos.row}, ${clickedPos.col})"
                            Log.d(TAG, "Selectare nouă (neadiacentă): $clickedPos")
                        }
                    }
                }
            )
        } else {
            Spacer(modifier = Modifier.height(200.dp))
        }
        Log.d(TAG, "GameBoard composition finished")
    }
}



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




@Preview(showBackground = true, widthDp = 380, heightDp = 600)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        GameScreen()
    }
}