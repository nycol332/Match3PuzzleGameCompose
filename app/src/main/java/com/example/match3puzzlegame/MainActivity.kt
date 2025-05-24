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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll // Pentru modifier-ul de scroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Menu // Sau Home


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
    // TODO: Adaugă alte tipuri (ex: CLEAR_BLOCKERS - curăță piese speciale)
}
// MODIFICAT: Adăugat WORLD_MAP
enum class ActiveScreen {
    MENU,
    GAME,
    RECIPE_BOOK,
    UPGRADES,
    WORLD_MAP // NOU: Ecran pentru harta lumii/nivelelor
    // Poți adăuga OPTIONS mai târziu
}

// --- Structura pentru Upgrade-uri ---
data class UpgradeInfo(
    val id: String, // Folosim String ID pentru flexibilitate (ex: "extra_moves", "faster_cooking")
    val name: String,
    val description: (Int) -> String, // Funcție pentru a genera descrierea bazată pe nivel
    val maxLevel: Int,
    val cost: (Int) -> Int // Funcție pentru a calcula costul nivelului următor (nivel curent -> cost)
)

data class TileMovementInfo(
    val originalRow: Int, // Sau null dacă e nouă
    val finalRow: Int,
    val col: Int,
    val tileType: Int, // Tipul final (pozitiv)
    val isNew: Boolean,
    val fallDistance: Int // Câte rânduri cade (0 dacă nu cade sau e nouă dar pe rândul 0)
)


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
    TILE_TYPE_1 to R.drawable.castravete,
    TILE_TYPE_2 to R.drawable.rosie,
    TILE_TYPE_3 to R.drawable.ceapa,
    TILE_TYPE_4 to R.drawable.porumb,
    TILE_TYPE_5 to R.drawable.cartof
)




//Liste

// --- Lista de Upgrade-uri Posibile ---
val availableUpgrades = listOf(
    UpgradeInfo(
        id = "extra_moves",
        name = "Mutări Bonus",
        description = { level -> "Începi fiecare nivel cu +$level mutări." },
        maxLevel = 5,
        cost = { level -> 100 * (level + 1) * (level + 1) }
    ),
    UpgradeInfo(
        id = "rare_ingredient_luck",
        name = "Noroc la Ingrediente",
        description = { level -> "Șansă +${5 * level}% să apară ingrediente mai rare." },
        maxLevel = 4,
        cost = { level -> 500 * (level + 1) }
    )
)



// --- Date Nivele Inițiale ---
val gameLevels = listOf(
    LevelData(
        levelId = 1,
        name = "Poiana Verde - Începuturi", // MODIFICAT Nume
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_2, 15)
        ),
        maxMoves = 20,
        unlocksRecipeIds = listOf(2)
    ),
    LevelData(
        levelId = 2,
        name = "Satul Vesel - Prima Comandă", // MODIFICAT Nume
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 1, 1)
        ),
        maxMoves = 25,
        unlocksRecipeIds = listOf(3)
    ),
    LevelData(
        levelId = 3,
        name = "Pădurea Umbroasă - Provizii", // MODIFICAT Nume
        objectives = listOf(
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_5, 20),
        ),
        maxMoves = 30,
        unlocksRecipeIds = listOf(4)
    ),
    LevelData(
        levelId = 4,
        name = "Târgul de la Răscruce - Festival", // MODIFICAT Nume
        objectives = listOf(
            LevelObjective(ObjectiveType.COOK_RECIPES, 2, 2),
            LevelObjective(ObjectiveType.COLLECT_INGREDIENTS, TILE_TYPE_4, 30)
        ),
        maxMoves = 35,
        unlocksRecipeIds = listOf(5)
    )
)

val allPossibleRecipes = listOf(
    Recipe(
        id = 1,
        name = "Salată Proaspătă",
        description = "Perfectă pentru o zi de vară.",
        ingredientsNeeded = mapOf(TILE_TYPE_1 to 5, TILE_TYPE_2 to 3, TILE_TYPE_3 to 2),
        sellingPrice = 30
    ),
    Recipe(
        id = 2,
        name = "Garnitură de Porumb",
        description = "Simplu și gustos.",
        ingredientsNeeded = mapOf(TILE_TYPE_4 to 8, TILE_TYPE_1 to 2),
        sellingPrice = 45
    ),
    Recipe(
        id = 3,
        name = "Tocăniță de Legume",
        description = "Sățioasă și aromată.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 6, TILE_TYPE_2 to 4, TILE_TYPE_3 to 3),
        sellingPrice = 70
    ),
    Recipe(
        id = 4,
        name = "Supă Cremă de Roșii",
        description = "Clasică și reconfortantă.",
        ingredientsNeeded = mapOf(TILE_TYPE_2 to 10, TILE_TYPE_3 to 4),
        sellingPrice = 55
    ),
    Recipe(
        id = 5,
        name = "Cartofi la Cuptor",
        description = "Cu ierburi aromatice.",
        ingredientsNeeded = mapOf(TILE_TYPE_5 to 12, TILE_TYPE_3 to 2),
        sellingPrice = 60
    )
)
val initialRecipes = allPossibleRecipes.filter { it.id == 1 }


//Funcții globale pure

fun getIngredientName(tileType: Int): String {
    return when (tileType) {
        TILE_TYPE_1 -> "Castravete" // MODIFICAT
        TILE_TYPE_2 -> "Roșie"     // MODIFICAT
        TILE_TYPE_3 -> "Ceapă"      // MODIFICAT
        TILE_TYPE_4 -> "Porumb"     // MODIFICAT
        TILE_TYPE_5 -> "Cartof"     // MODIFICAT
        else -> "Necunoscut"
    }
}

private fun playSound(context: Context, soundResourceId: Int) {
    try {
        val mp = MediaPlayer.create(context, soundResourceId)
        if (mp == null) {
            Log.e(TAG, "playSound: MediaPlayer.create returned null for resource ID: $soundResourceId")
            return
        }
        mp.setOnCompletionListener { mediaPlayer ->
            mediaPlayer?.release()
            Log.d(TAG, "playSound: MediaPlayer released for resource ID: $soundResourceId")
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "playSound: MediaPlayer error! what: $what, extra: $extra for resource ID: $soundResourceId")
            mp?.release()
            true
        }
        mp.start()
    } catch (e: Exception) {
        Log.e(TAG, "playSound: Exception while trying to play sound ID: $soundResourceId", e)
    }
}


// --- MainActivity  ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity: onCreate - START")
        setContent {
            Log.d(TAG, "MainActivity: setContent - START")
            Match3PuzzleGameTheme {
                Log.d(TAG, "MainActivity: Match3PuzzleGameTheme - START")
                Match3GameApp()
                Log.d(TAG, "MainActivity: Match3PuzzleGameTheme - END")
            }
            Log.d(TAG, "MainActivity: setContent - END")
        }
        Log.d(TAG, "MainActivity: onCreate - END")
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradesScreen(
    allPossibleUpgrades: List<UpgradeInfo>,
    currentOwnedUpgrades: Map<String, Int>,
    currentPlayerMoney: Int,
    onPurchaseUpgrade: (upgradeId: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atelier Îmbunătățiri") },
                navigationIcon = {
                    IconButton(onClick = {
                        playSound(context, R.raw.click)
                        onClose()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.coin),
                            contentDescription = "Bani",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentPlayerMoney.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (allPossibleUpgrades.isEmpty()){
                item {
                    Text(
                        "Nicio îmbunătățire disponibilă momentan.",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(allPossibleUpgrades, key = { it.id }) { upgradeInfo ->
                val currentLevel = currentOwnedUpgrades[upgradeInfo.id] ?: 0
                val isMaxLevel = currentLevel >= upgradeInfo.maxLevel
                val costForNextLevel = if (!isMaxLevel) upgradeInfo.cost(currentLevel) else 0
                val canAfford = currentPlayerMoney >= costForNextLevel && !isMaxLevel

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(upgradeInfo.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Nivel: $currentLevel / ${upgradeInfo.maxLevel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Efect Curent: ${upgradeInfo.description(currentLevel)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isMaxLevel) {
                            Text(
                                "NIVEL MAXIM ATINS",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.align(Alignment.End)
                            )
                        } else {
                            Text(
                                "Următorul Nivel: ${upgradeInfo.description(currentLevel + 1)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    playSound(context, R.raw.click)
                                    onPurchaseUpgrade(upgradeInfo.id)
                                },
                                enabled = canAfford,
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (canAfford) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Îmbunătățește (${costForNextLevel} Bani)")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Composable Părinte care Deține Starea și Logica ---
@Composable
fun Match3GameApp() {
    var currentActiveScreen by remember { mutableStateOf(ActiveScreen.MENU) }
    var previousActiveScreen by remember { mutableStateOf(ActiveScreen.MENU) }
    Log.d(TAG, "Match3GameApp RECOMPOSED: current=$currentActiveScreen, previous=$previousActiveScreen")
    var inventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var feedbackMessage by remember { mutableStateOf("") }
    var board by remember { mutableStateOf(emptyList<MutableList<Int>>()) }
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
    var movesLeft by remember { mutableStateOf(0) }
    var objectiveProgress by remember { mutableStateOf<Map<LevelObjective, Int>>(emptyMap()) }
    var gameState by remember { mutableStateOf("Playing") }
    var selectedRecipeToShow by remember { mutableStateOf<Recipe?>(null) }
    var availableRecipes by remember { mutableStateOf(initialRecipes.toMutableList()) }
    val context = LocalContext.current
    var playerXP by remember { mutableStateOf(0) }
    var playerMoney by remember { mutableStateOf(100) }
    var cookedMealsInventory by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val density = LocalDensity.current
    var currentTileMovements by remember { mutableStateOf<List<TileMovementInfo>>(emptyList()) }
    var playerUpgrades by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showShopDialog by remember { mutableStateOf(false) }
    // NOU: Stare pentru a ține minte până la ce nivel a ajuns jucătorul
    var maxUnlockedLevelIndex by remember { mutableStateOf(0) } // Primul nivel (index 0) e mereu deblocat


    fun purchaseUpgrade(upgradeId: String) {
        val upgradeInfo = availableUpgrades.find { it.id == upgradeId }
        if (upgradeInfo == null) {
            feedbackMessage = "Eroare: Îmbunătățire necunoscută!"
            playSound(context, R.raw.lost)
            return
        }
        val currentLevel = playerUpgrades[upgradeId] ?: 0
        if (currentLevel >= upgradeInfo.maxLevel) {
            feedbackMessage = "${upgradeInfo.name} este deja la Nivel Maxim!"
            return
        }
        val costForNextLevel = upgradeInfo.cost(currentLevel)
        if (playerMoney >= costForNextLevel) {
            playerMoney -= costForNextLevel
            val newLevel = currentLevel + 1
            playerUpgrades = playerUpgrades + (upgradeId to newLevel)
            feedbackMessage = "${upgradeInfo.name} a ajuns la Nivelul $newLevel!"
            playSound(context, R.raw.coin)
        } else {
            feedbackMessage = "Nu ai ${costForNextLevel} Bani pentru ${upgradeInfo.name}!"
            playSound(context, R.raw.lost)
        }
    }

    fun sellCookedMeals() {
        if (cookedMealsInventory.isEmpty()) {
            feedbackMessage = "Nu ai ce vinde!"
            return
        }
        var moneyEarned = 0
        cookedMealsInventory.forEach { (recipeId, quantity) ->
            val recipe = allPossibleRecipes.find { it.id == recipeId }
            if (recipe != null) {
                moneyEarned += recipe.sellingPrice * quantity
            }
        }
        playerMoney += moneyEarned
        cookedMealsInventory = emptyMap()
        feedbackMessage = "Ai vândut marfa pentru $moneyEarned Bani!"
        playSound(context, R.raw.coin)
    }

    // MODIFICAT: Funcția navigateTo pentru a gestiona mai bine `previousActiveScreen`
    fun navigateTo(destination: ActiveScreen) {
        if (currentActiveScreen != destination) {
            Log.d(TAG, "navigateTo: DEST=$destination, CURRENT_BEFORE_CHANGE=$currentActiveScreen")

            // Salvează ecranul curent (care va deveni anterior)
            // DOAR dacă NU ne întoarcem la MENIU sau la HARTA (care sunt ecrane "principale")
            // și NU mergem SPRE MENIU sau HARTA (pentru a nu suprascrie un 'previous' valid)
            if (destination !in listOf(ActiveScreen.MENU, ActiveScreen.WORLD_MAP) &&
                currentActiveScreen !in listOf(ActiveScreen.MENU, ActiveScreen.WORLD_MAP)) {
                previousActiveScreen = currentActiveScreen
                Log.d(TAG, "   SAVED previousActiveScreen = $currentActiveScreen because navigating to a sub-screen ($destination) from a sub-screen ($currentActiveScreen)")
            } else if (destination in listOf(ActiveScreen.RECIPE_BOOK, ActiveScreen.UPGRADES, ActiveScreen.GAME) &&
                currentActiveScreen in listOf(ActiveScreen.MENU, ActiveScreen.WORLD_MAP)) {
                // Dacă plecăm de la MENIU sau HARTA spre un ecran secundar (REȚETE, UPGRADES, JOC)
                previousActiveScreen = currentActiveScreen
                Log.d(TAG, "   SAVED previousActiveScreen = $currentActiveScreen because navigating from a main screen ($currentActiveScreen) to a sub-screen ($destination)")
            }


            currentActiveScreen = destination
            Log.d(TAG, "   NEW currentActiveScreen = $destination. Previous is now: $previousActiveScreen")
        }
    }


    fun findMatchesOnBoard(targetBoard: List<List<Int>>): Set<TilePosition> {
        val matches = mutableSetOf<TilePosition>()
        for (r in 0 until ROWS) {
            var currentStreak = 1
            var currentType = -1
            for (c in 0 until COLS) {
                val tileType = targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) { matches.add(TilePosition(r, c - i)) }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) { matches.add(TilePosition(r, COLS - i)) }
            }
        }
        for (c in 0 until COLS) {
            var currentStreak = 1
            var currentType = -1
            for (r in 0 until ROWS) {
                val tileType = targetBoard.getOrNull(r)?.getOrNull(c) ?: EMPTY_TILE
                if (tileType != EMPTY_TILE && tileType == currentType) {
                    currentStreak++
                } else {
                    if (currentStreak >= 3) {
                        for (i in 1..currentStreak) { matches.add(TilePosition(r - i, c)) }
                    }
                    currentType = tileType
                    currentStreak = if (tileType != EMPTY_TILE) 1 else 0
                }
            }
            if (currentStreak >= 3) {
                for (i in 1..currentStreak) { matches.add(TilePosition(ROWS - i, c)) }
            }
        }
        return matches
    }

    fun generateValidInitialBoard(): List<MutableList<Int>> {
        var attempts = 0
        while (attempts < 100) {
            val candidateBoard = List(ROWS) { MutableList(COLS) { TILE_TYPES.random() } }
            val initialMatches = findMatchesOnBoard(candidateBoard)
            if (initialMatches.isEmpty()) {
                return candidateBoard
            }
            attempts++
        }
        Log.w(TAG, "Could not generate a match-free initial board. Using last generated board (may have matches).")
        return List(ROWS) { MutableList(COLS) { TILE_TYPES.random() } } // Fallback
    }

    LaunchedEffect(Unit) {
        board = generateValidInitialBoard()
    }

    // MODIFICAT: Adăugat parametru `maxUnlockedLevelIndex` și logica de actualizare
    fun checkLevelEndCondition(progressToCheck: Map<LevelObjective, Int> = objectiveProgress) {
        if (gameState != "Playing") return
        if (currentLevelData == null) return

        Log.d(TAG, "--- Checking Level End Condition for Level ${currentLevelData.levelId} ---")
        Log.d(TAG, "Checking with Progress State: $progressToCheck")

        var allMet = true
        currentLevelData.objectives.forEach { objective ->
            val progress = progressToCheck[objective] ?: 0
            val target = objective.targetQuantity
            val isMet = progress >= target
            if (!isMet) allMet = false
        }
        Log.d(TAG, "Final check: All Objectives Met = $allMet")

        if (allMet) {
            Log.i(TAG, "Level ${currentLevelData.levelId} WON!")
            gameState = "Won"

            // NOU: Actualizează `maxUnlockedLevelIndex` dacă acest nivel era cel mai avansat deblocat
            val currentLevelActualIndex = gameLevels.indexOfFirst { it.levelId == currentLevelData.levelId }
            if (currentLevelActualIndex != -1 && currentLevelActualIndex == maxUnlockedLevelIndex) {
                if (maxUnlockedLevelIndex < gameLevels.size - 1) { // Dacă nu e ultimul nivel din joc
                    maxUnlockedLevelIndex++
                    Log.i(TAG, "Unlocked next level. maxUnlockedLevelIndex is now: $maxUnlockedLevelIndex")
                } else {
                    Log.i(TAG, "All levels completed! maxUnlockedLevelIndex remains at ${gameLevels.size -1}")
                }
            }


            val newlyUnlockedRecipes = mutableListOf<String>()
            currentLevelData.unlocksRecipeIds.forEach { recipeId ->
                if (availableRecipes.none { it.id == recipeId }) {
                    allPossibleRecipes.find { it.id == recipeId }?.let { recipeToAdd ->
                        availableRecipes = (availableRecipes + recipeToAdd).toMutableList()
                        newlyUnlockedRecipes.add(recipeToAdd.name)
                    }
                }
            }
            var winMessage = "Nivel ${currentLevelData.levelId} Terminat!"
            if (newlyUnlockedRecipes.isNotEmpty()) {
                winMessage += "\nRețete noi: ${newlyUnlockedRecipes.joinToString()}"
            }
            feedbackMessage = winMessage
            playSound(context, R.raw.win)
        } else if (movesLeft <= 0) {
            Log.i(TAG, "Level ${currentLevelData.levelId} LOST! No moves left.")
            gameState = "Lost"
            playSound(context, R.raw.lost)
            feedbackMessage = "Ai rămas fără mutări! Reîncearcă!"
        }
    }


    fun handleTileClick(row: Int, col: Int) {
        if (isProcessing || !swapAnimationFinished || gameState != "Playing") return

        val clickedPos = TilePosition(row, col)
        val currentSelection = selectedTilePos
        playSound(context, R.raw.click)

        fun areAdjacent(pos1: TilePosition, pos2: TilePosition): Boolean {
            val rowDiff = abs(pos1.row - pos2.row)
            val colDiff = abs(pos1.col - pos2.col)
            return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
        }
        if (currentSelection == null) {
            selectedTilePos = clickedPos
        } else {
            if (clickedPos == currentSelection) {
                selectedTilePos = null
            } else if (areAdjacent(currentSelection, clickedPos)) {
                if (movesLeft > 0) {
                    movesLeft -= 1
                    selectedTilePos = null
                    swappingTiles = Pair(currentSelection, clickedPos)
                    swapAnimationFinished = false
                } else {
                    feedbackMessage = "Fără mutări!"
                    selectedTilePos = null
                    checkLevelEndCondition()
                }
            } else {
                selectedTilePos = clickedPos
            }
        }
    }


    fun calculateGravityAndFill(currentBoardState: List<List<Int>>): Pair<List<MutableList<Int>>, List<TileMovementInfo>> {
        val numRows = currentBoardState.size
        val numCols = currentBoardState.firstOrNull()?.size ?: 0
        if (numCols == 0) return Pair(emptyList(), emptyList())

        val finalBoardState = List(numRows) { MutableList(numCols) { EMPTY_TILE } }
        val movements = mutableListOf<TileMovementInfo>()

        for (c in 0 until numCols) {
            var writeRow = numRows - 1
            for (r in numRows - 1 downTo 0) {
                val currentType = currentBoardState[r][c]
                if (currentType != EMPTY_TILE) {
                    val fallDistance = writeRow - r
                    movements.add(TileMovementInfo(r, writeRow, c, currentType, false, fallDistance))
                    finalBoardState[writeRow][c] = currentType
                    writeRow--
                }
            }
            for (r in writeRow downTo 0) {
                val newType = TILE_TYPES.random()
                finalBoardState[r][c] = newType
                movements.add(TileMovementInfo(-1, r, c, newType, true, r + 1))
            }
        }
        return Pair(finalBoardState, movements)
    }


    suspend fun processMatchesAndCascades() {
        var currentBoardInternal = board
        var cascadeCount = 0

        while(true) {
            val matches = findMatchesOnBoard(currentBoardInternal)
            if (matches.isEmpty()) {
                checkLevelEndCondition()
                break
            }
            playSound(context, R.raw.potrivire)
            cascadeCount++
            val ingredientsEarnedThisMatch = mutableMapOf<Int, Int>()
            matches.forEach { pos ->
                if (pos.row in 0 until ROWS && pos.col in 0 until COLS) {
                    val tileType = currentBoardInternal.getOrNull(pos.row)?.getOrNull(pos.col)
                    if (tileType != null && tileType != EMPTY_TILE) {
                        ingredientsEarnedThisMatch[tileType] = ingredientsEarnedThisMatch.getOrDefault(tileType, 0) + 1
                    }
                }
            }
            // MODIFICAT: Persistența inventarului - adăugăm la inventarul existent
            if (ingredientsEarnedThisMatch.isNotEmpty()) {
                val updatedInventory = inventory.toMutableMap()
                ingredientsEarnedThisMatch.forEach { (ingredientId, quantity) ->
                    updatedInventory[ingredientId] = updatedInventory.getOrDefault(ingredientId, 0) + quantity
                }
                inventory = updatedInventory // Actualizează starea globală a inventarului
                Log.d(TAG, "Inventory updated by match: $inventory")
            }


            if (ingredientsEarnedThisMatch.isNotEmpty()) {
                val updatedProgressCollect = objectiveProgress.toMutableMap()
                currentLevelData?.objectives?.forEach { objective ->
                    if (objective.type == ObjectiveType.COLLECT_INGREDIENTS) {
                        val ingredientId = objective.targetId
                        val collectedNow = ingredientsEarnedThisMatch.getOrDefault(ingredientId, 0)
                        if (collectedNow > 0) {
                            val currentProg = updatedProgressCollect[objective] ?: 0
                            updatedProgressCollect[objective] = (currentProg + collectedNow).coerceAtMost(objective.targetQuantity)
                        }
                    }
                }
                objectiveProgress = updatedProgressCollect
            }
            val feedbackParts = ingredientsEarnedThisMatch.map { "+${it.value} ${getIngredientName(it.key)}" }
            feedbackMessage = if (cascadeCount > 1) "Cascadă $cascadeCount! ${feedbackParts.joinToString()}" else "Potrivire! ${feedbackParts.joinToString()}"

            tilesBeingMatched = matches
            delay(400L)
            tilesBeingMatched = emptySet()

            val boardWithEmptyTiles = currentBoardInternal.map { it.toMutableList() }
            matches.forEach { pos -> boardWithEmptyTiles[pos.row][pos.col] = EMPTY_TILE }
            board = boardWithEmptyTiles
            currentBoardInternal = boardWithEmptyTiles

            val (finalBoardState, tileMovements) = calculateGravityAndFill(currentBoardInternal)
            if (tileMovements.isEmpty()) {
                board = finalBoardState
                currentBoardInternal = finalBoardState
                continue
            }
            currentTileMovements = tileMovements
            val maxFallDelay = 30L * COLS + 10L * ROWS
            val fallAnimDuration = 350L
            val totalWaitTime = fallAnimDuration + maxFallDelay + 150L
            delay(totalWaitTime)

            board = finalBoardState
            currentTileMovements = emptyList()
            currentBoardInternal = finalBoardState
        }
    }

    fun performValidSwapAndProcess(pos1: TilePosition, pos2: TilePosition) {
        val newBoard = board.map { it.toMutableList() }
        val temp = newBoard[pos1.row][pos1.col]
        newBoard[pos1.row][pos1.col] = newBoard[pos2.row][pos2.col]
        newBoard[pos2.row][pos2.col] = temp
        board = newBoard
        isProcessing = true
        scope.launch {
            processMatchesAndCascades()
            checkLevelEndCondition()
            isProcessing = false
        }
    }

    fun canCookRecipe(recipe: Recipe): Boolean {
        return recipe.ingredientsNeeded.all { (ingredientId, quantityNeeded) ->
            (inventory.getOrDefault(ingredientId, 0)) >= quantityNeeded
        }
    }

    fun cookRecipe(recipe: Recipe) {
        if (!canCookRecipe(recipe)) {
            feedbackMessage = "Nu ai suficiente ingrediente pentru ${recipe.name}!"
            return
        }
        val updatedInventory = inventory.toMutableMap()
        recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
            updatedInventory[ingredientId] = (updatedInventory.getOrDefault(ingredientId, 0) - quantityNeeded).coerceAtLeast(0)
        }
        inventory = updatedInventory
        playSound(context, R.raw.gatire)
        playerXP += 50
        val currentCookedAmount = cookedMealsInventory.getOrDefault(recipe.id, 0)
        cookedMealsInventory = cookedMealsInventory + (recipe.id to currentCookedAmount + 1)

        val updatedProgress = objectiveProgress.toMutableMap()
        currentLevelData?.objectives?.forEach { objective ->
            if (objective.type == ObjectiveType.COOK_RECIPES && objective.targetId == recipe.id) {
                val currentProg = updatedProgress[objective] ?: 0
                updatedProgress[objective] = (currentProg + 1).coerceAtMost(objective.targetQuantity)
            }
        }
        objectiveProgress = updatedProgress
        feedbackMessage = "Ai gătit ${recipe.name}! +50 XP"
        selectedRecipeToShow = null
        checkLevelEndCondition(updatedProgress)
    }

    // MODIFICAT: goToNextLevel nu mai incrementează direct currentLevelIndex, ci se bazează pe logica din checkLevelEndCondition
    // și navigarea la hartă. Această funcție ar putea fi folosită de un buton "Următorul Nivel Deblocat" pe hartă,
    // dar pentru moment o lăsăm așa, va fi apelată de butonul "Nivelul Următor" din GameScreen, care apoi va naviga la hartă.
    fun goToNextLevel() {
        Log.d(TAG, "goToNextLevel called. Current index: $currentLevelIndex")
        playSound(context, R.raw.click)
        // Logica de incrementare a currentLevelIndex va fi gestionată de selecția de pe hartă.
        // Aici, doar schimbăm starea jocului dacă e cazul și eventual navigăm la hartă.
        if (currentLevelIndex < gameLevels.size - 1) {
            // Nu mai schimbăm currentLevelIndex aici direct.
            // Navigăm la hartă unde jucătorul va alege următorul nivel.
            navigateTo(ActiveScreen.WORLD_MAP)
            feedbackMessage = "Alege următorul nivel de pe hartă!"
        } else {
            Log.d(TAG, "All levels finished!")
            gameState = "Finished" // Stare finală a jocului
            feedbackMessage = "Felicitări! Ai terminat toate nivelele!"
            navigateTo(ActiveScreen.WORLD_MAP) // Chiar și dacă a terminat, îl ducem la hartă
        }
    }

    // MODIFICAT: retryLevel acum resetează starea și reîncarcă nivelul curent, apoi navighează la GameScreen
    fun retryLevel() {
        Log.d(TAG, "retryLevel called for index: $currentLevelIndex")
        playSound(context, R.raw.click)
        val levelToRetryIndex = currentLevelIndex // Păstrăm indexul curent

        // Forțăm re-rularea LaunchedEffect pentru resetarea stării nivelului
        // Setăm un index invalid temporar, apoi revenim la cel corect
        currentLevelIndex = -1
        scope.launch {
            delay(50) // Mic delay pentru a permite recompoziția
            currentLevelIndex = levelToRetryIndex
            // După ce LaunchedEffect(currentLevelIndex) a resetat starea pentru nivelul corect,
            // navigăm înapoi la GameScreen.
            navigateTo(ActiveScreen.GAME) // Asigură-te că ajungi în GameScreen
            Log.d(TAG, "Retry: Level state reset for index $currentLevelIndex, navigating to GAME.")
        }
    }


    // MODIFICAT: `LaunchedEffect(currentLevelIndex)` - scos resetarea `inventory` și `cookedMealsInventory`
    LaunchedEffect(currentLevelIndex) {
        Log.d(TAG, "--- LaunchedEffect triggered for level index: $currentLevelIndex ---")
        val levelData = gameLevels.getOrNull(currentLevelIndex)

        if (levelData != null && currentLevelIndex != -1) { // Adăugăm currentLevelIndex != -1 pentru a evita rularea pe starea invalidă
            Log.d(TAG, "Resetting state for Level ${levelData.levelId}: ${levelData.name}")

            val extraMovesLevel = playerUpgrades["extra_moves"] ?: 0
            val bonusMoves = extraMovesLevel
            val totalInitialMoves = levelData.maxMoves + bonusMoves

            board = generateValidInitialBoard()
            movesLeft = totalInitialMoves
            objectiveProgress = levelData.objectives.associateWith { 0 }
            // inventory = emptyMap() // STERS: Inventarul nu se mai resetează aici
            gameState = "Playing"
            selectedTilePos = null
            tilesBeingMatched = emptySet()
            isProcessing = false
            swapAnimationFinished = true
            swappingTiles = null
            currentTileMovements = emptyList()
            // cookedMealsInventory = emptyMap() // STERS: Inventarul de mâncare gătită nu se mai resetează aici

            val firstObjectiveDescription = if (levelData.objectives.isNotEmpty()) {
                formatObjective(levelData.objectives.first(), 0)
            } else "Distrează-te!"
            feedbackMessage = "Nivel ${levelData.levelId}: ${levelData.name}\n${firstObjectiveDescription}\nMutări: $totalInitialMoves"
            Log.d(TAG, "Initial feedback: $feedbackMessage")
        } else if (currentLevelIndex != -1) { // Doar dacă nu e indexul invalid temporar
            Log.e(TAG, "Invalid level index or game finished: $currentLevelIndex")
            // ... restul logicii de eroare/final joc ...
        }
    }

    LaunchedEffect(swappingTiles) {
        val tilesToAnimate = swappingTiles
        if (tilesToAnimate != null) {
            val (pos1, pos2) = tilesToAnimate
            val xDiff = (pos2.col - pos1.col)
            val yDiff = (pos2.row - pos1.row)
            var tileSizePx = 0f
            playSound(context, R.raw.swap)
            with(density) { tileSizePx = 45.dp.toPx() }

            val job1 = scope.launch {
                tile1Offset.snapTo(IntOffset.Zero)
                tile1Offset.animateTo(
                    targetValue = IntOffset(x = (xDiff * tileSizePx).toInt(), y = (yDiff * tileSizePx).toInt()),
                    animationSpec = tween(durationMillis = 250)
                )
            }
            val job2 = scope.launch {
                tile2Offset.snapTo(IntOffset.Zero)
                tile2Offset.animateTo(
                    targetValue = IntOffset(x = (-xDiff * tileSizePx).toInt(), y = (-yDiff * tileSizePx).toInt()),
                    animationSpec = tween(durationMillis = 250)
                )
            }
            job1.join()
            job2.join()

            val boardCheck = board.map { it.toMutableList() }
            val tempCheck = boardCheck[pos1.row][pos1.col]
            boardCheck[pos1.row][pos1.col] = boardCheck[pos2.row][pos2.col]
            boardCheck[pos2.row][pos2.col] = tempCheck
            val potentialMatches = findMatchesOnBoard(boardCheck)

            if (potentialMatches.isNotEmpty()) {
                tile1Offset.snapTo(IntOffset.Zero)
                tile2Offset.snapTo(IntOffset.Zero)
                performValidSwapAndProcess(pos1, pos2)
            } else {
                feedbackMessage = "Fără potrivire..."
                playSound(context, R.raw.swap_fail)
                val jobBack1 = scope.launch { tile1Offset.animateTo(IntOffset.Zero, tween(150)) }
                val jobBack2 = scope.launch { tile2Offset.animateTo(IntOffset.Zero, tween(150)) }
                jobBack1.join()
                jobBack2.join()
                checkLevelEndCondition()
            }
            swappingTiles = null
            swapAnimationFinished = true
        }
    }

    // === DECIZIA DE AFIȘARE A ECRANULUI PRINCIPAL ===
    // MODIFICAT: Adăugat cazul pentru WORLD_MAP
    when (currentActiveScreen) {
        ActiveScreen.MENU -> {
            MainMenuScreen(
                // MODIFICAT: onNavigateToGame duce la WORLD_MAP
                onNavigateToGame = { navigateTo(ActiveScreen.WORLD_MAP) },
                onNavigateToRecipeBook = { navigateTo(ActiveScreen.RECIPE_BOOK) },
                onNavigateToShop = { playSound(context, R.raw.click); showShopDialog = true },
                onNavigateToUpgrades = { navigateTo(ActiveScreen.UPGRADES) }
            )
        }
        ActiveScreen.WORLD_MAP -> { // NOU: Caz pentru afișarea hărții
            WorldMapScreen(
                allLevels = gameLevels,
                maxUnlockedLevelIndex = maxUnlockedLevelIndex,
                onSelectLevel = { selectedLevelIndex ->
                    currentLevelIndex = selectedLevelIndex // Setează nivelul curent
                    navigateTo(ActiveScreen.GAME) // Navighează la ecranul de joc
                },
                onNavigateToMenu = { navigateTo(ActiveScreen.MENU) },
                // NOU: Callback-uri pentru acces la Rețete și Upgrade-uri de pe hartă
                onNavigateToRecipeBook = { navigateTo(ActiveScreen.RECIPE_BOOK) },
                onNavigateToUpgrades = { navigateTo(ActiveScreen.UPGRADES) },
                playerMoney = playerMoney // Trimitem banii pentru afișare (opțional)
            )
        }
        ActiveScreen.GAME -> {
            GameScreen(
                movesLeft = movesLeft,
                currentLevelData = currentLevelData,
                objectiveProgress = objectiveProgress,
                feedbackMessage = feedbackMessage,
                inventory = inventory,
                board = board,
                selectedTilePosition = selectedTilePos,
                tilesBeingMatched = tilesBeingMatched,
                isProcessing = isProcessing || !swapAnimationFinished,
                gameState = gameState,
                swappingTilesInfo = swappingTiles,
                tile1AnimatedOffset = tile1Offset.value,
                tile2AnimatedOffset = tile2Offset.value,
                playerXP = playerXP,
                availableRecipesCount = availableRecipes.size,
                playerMoney = playerMoney,
                currentLevelId = currentLevelData?.levelId ?: -1,
                tileMovements = currentTileMovements,
                onTileClick = ::handleTileClick,
                onShowShop = { playSound(context, R.raw.click); showShopDialog = true },
                onRetryLevel = ::retryLevel,
                onNextLevel = ::goToNextLevel, // Acum va duce la WORLD_MAP
                onShowRecipeBook = { navigateTo(ActiveScreen.RECIPE_BOOK) },
                onShowUpgrades = { navigateTo(ActiveScreen.UPGRADES) },
                // MODIFICAT: Butonul de "Meniu" din GameScreen duce la WORLD_MAP
                onNavigateBackToMenu = { navigateTo(ActiveScreen.WORLD_MAP) }
            )
        }
        ActiveScreen.RECIPE_BOOK -> {
            RecipeBookScreen(
                recipes = availableRecipes,
                inventory = inventory,
                canCookChecker = ::canCookRecipe,
                onCookRecipe = ::cookRecipe,
                onShowRecipeDetails = { recipe -> selectedRecipeToShow = recipe },
                onClose = {
                    playSound(context, R.raw.click)
                    // MODIFICAT: Folosește `previousActiveScreen` stocat corect
                    navigateTo(previousActiveScreen) // Se întoarce la ecranul anterior (Meniu sau Hartă)
                }
            )
        }
        ActiveScreen.UPGRADES -> {
            UpgradesScreen(
                allPossibleUpgrades = availableUpgrades,
                currentOwnedUpgrades = playerUpgrades,
                currentPlayerMoney = playerMoney,
                onPurchaseUpgrade = ::purchaseUpgrade,
                onClose = {
                    playSound(context, R.raw.click)
                    // MODIFICAT: Folosește `previousActiveScreen` stocat corect
                    navigateTo(previousActiveScreen) // Se întoarce la ecranul anterior (Meniu sau Hartă)
                }
            )
        }
    }

    if (selectedRecipeToShow != null) {
        RecipeDetailDialog(
            recipe = selectedRecipeToShow!!,
            inventory = inventory,
            canCookChecker = ::canCookRecipe,
            onCook = { recipeToCook ->
                cookRecipe(recipeToCook)
                selectedRecipeToShow = null
            },
            onDismiss = {
                playSound(context, R.raw.click)
                selectedRecipeToShow = null
            }
        )
    }

    if (showShopDialog) {
        ShopDialog(
            cookedMeals = cookedMealsInventory,
            recipesData = allPossibleRecipes,
            onSellAll = {
                sellCookedMeals()
                showShopDialog = false
            },
            onDismiss = {
                playSound(context, R.raw.click)
                showShopDialog = false
            }
        )
    }
}


// --- GameScreen Composable ---
// MODIFICAT: `onNavigateBackToMenu` va duce la Hartă, nu la Meniu direct
@Composable
fun GameScreen(
    movesLeft: Int,
    currentLevelData: LevelData?,
    objectiveProgress: Map<LevelObjective, Int>,
    feedbackMessage: String,
    inventory: Map<Int, Int>,
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
    isProcessing: Boolean,
    gameState: String,
    playerXP: Int,
    playerMoney: Int,
    availableRecipesCount: Int,
    swappingTilesInfo: Pair<TilePosition, TilePosition>?,
    tile1AnimatedOffset: IntOffset,
    tile2AnimatedOffset: IntOffset,
    onTileClick: (row: Int, col: Int) -> Unit,
    onShowRecipeBook: () -> Unit,
    onRetryLevel: () -> Unit,
    onNextLevel: () -> Unit,
    currentLevelId: Int,
    onShowShop: () -> Unit,
    onShowUpgrades: () -> Unit,
    tileMovements: List<TileMovementInfo>,
    onNavigateBackToMenu: () -> Unit // Callback pentru a naviga înapoi (la Hartă)
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Rând Superior: Buton Înapoi, XP, Bani, Rețete, Shop, Upgrades ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, // MODIFICAT pentru a spația mai bine
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MODIFICAT: Butonul este acum "Înapoi la Hartă"
            IconButton(onClick = {
                playSound(context, R.raw.click)
                onNavigateBackToMenu() // Acesta duce la hartă
            }) {
                // Poți schimba iconița dacă dorești (ex: o hartă mică sau o săgeată specifică)
                Icon(Icons.Filled.ArrowBack, contentDescription = "Înapoi la Hartă")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("XP:", fontSize = 16.sp, fontWeight = FontWeight.Bold) // Font mai mic
                Spacer(modifier = Modifier.width(4.dp))
                Text(playerXP.toString(), fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(id = R.drawable.coin), contentDescription = "Bani", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(playerMoney.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { playSound(context, R.raw.click); onShowRecipeBook() }
            ) {
                Image(painterResource(id = R.drawable.carte), contentDescription = "Rețete", modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(availableRecipesCount.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { playSound(context, R.raw.click); onShowShop() }) {
                Image(painterResource(id = R.drawable.market), contentDescription = "Magazin", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { playSound(context, R.raw.click); onShowUpgrades() }) {
                Image(painterResource(id = R.drawable.upgrade), contentDescription = "Îmbunătățiri", modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- Butoane Victorie/Înfrângere ---
        if (gameState == "Won") {
            Button(onClick = {
                playSound(context, R.raw.click)
                onNextLevel() // Acum onNextLevel va naviga la hartă
            }) { Text("Continuă spre Hartă") } // MODIFICAT text buton
        } else if (gameState == "Lost") {
            Button(onClick = {
                playSound(context, R.raw.click)
                onRetryLevel()
            }) { Text("Reîncearcă Nivelul") }
        }


        if (currentLevelData != null && gameState == "Playing") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Nivel ${currentLevelData.levelId}: ${currentLevelData.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mutări: $movesLeft",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (movesLeft <= 5 && movesLeft > 0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    val firstUnmetObjective = currentLevelData.objectives.firstOrNull { (objectiveProgress[it] ?: 0) < it.targetQuantity }
                    if (firstUnmetObjective != null) {
                        val progress = objectiveProgress[firstUnmetObjective] ?: 0
                        Text("🎯 ${formatObjective(firstUnmetObjective, progress)}", style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp)
                    } else if (currentLevelData.objectives.isNotEmpty()) {
                        Text("Obiectiv realizat! ✅", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = feedbackMessage,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Inventar Ingrediente:", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = if (inventory.isEmpty()) Arrangement.Center else Arrangement.Start
        ) {
            if (inventory.isEmpty()) {
                Text("Rucsacul e gol!", fontSize = 12.sp, color = Color.Gray)
            } else {
                inventory.entries.sortedBy { it.key }.forEach { (ingredientId, quantity) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                        val drawableResId = tileDrawables[ingredientId]
                        if (drawableResId != null) {
                            Image(painter = painterResource(id = drawableResId), contentDescription = getIngredientName(ingredientId), modifier = Modifier.size(28.dp))
                        } else {
                            Box(Modifier.size(28.dp).background(tileColors[ingredientId] ?: Color.Gray, CircleShape))
                        }
                        Text(text = quantity.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.weight(0.8f)) {
            if (gameState == "Playing" || gameState == "Won" || gameState == "Lost") {
                GameBoard(
                    onTileClick = { row, col -> if (gameState == "Playing" && !isProcessing) onTileClick(row, col) },
                    board = board,
                    selectedTilePosition = selectedTilePosition,
                    tilesBeingMatched = tilesBeingMatched,
                    swappingTilesInfo = swappingTilesInfo,
                    tile1AnimatedOffset = tile1AnimatedOffset,
                    tile2AnimatedOffset = tile2AnimatedOffset,
                    tileMovements = tileMovements
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}


fun formatObjective(objective: LevelObjective, progress: Int): String {
    val targetQuantity = objective.targetQuantity
    val displayProgress = progress.coerceAtMost(targetQuantity)
    return when (objective.type) {
        ObjectiveType.COLLECT_INGREDIENTS -> "Colectează ${getIngredientName(objective.targetId)}: $displayProgress/$targetQuantity"
        ObjectiveType.COOK_RECIPES -> "Gătește ${allPossibleRecipes.find { it.id == objective.targetId }?.name ?: "Rețetă"}: $displayProgress/$targetQuantity"
    }
}


// MODIFICAT: `onClose` din RecipeBookScreen acum folosește previousActiveScreen gestionat mai bine
@Composable
fun RecipeBookScreen(
    recipes: List<Recipe>,
    inventory: Map<Int, Int>,
    canCookChecker: (Recipe) -> Boolean,
    onCookRecipe: (Recipe) -> Unit,
    onShowRecipeDetails: (Recipe) -> Unit,
    onClose: () -> Unit // Acest onClose va fi apelat de Match3GameApp pentru a naviga la previousActiveScreen
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                playSound(context, R.raw.click)
                onClose() // Apelează callback-ul care va naviga la previousActiveScreen
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cartea Mea de Bucate", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recipes, key = { it.id }) { recipe ->
                // val canCook = remember(inventory, recipe) { canCookChecker(recipe) } // Nu mai e nevoie aici, e in dialog
                Column(
                    modifier = Modifier
                        .clickable {
                            playSound(context, R.raw.click)
                            onShowRecipeDetails(recipe)
                        }
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium) // Afișează numele
                    Text(recipe.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Divider()
            }
            if (recipes.isEmpty()) {
                item { Text("Nicio rețetă deblocată încă.", modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}


@Composable
fun RecipeDetailDialog(
    recipe: Recipe,
    inventory: Map<Int, Int>,
    canCookChecker: (Recipe) -> Boolean,
    onCook: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    val canCookCurrentRecipe = remember(inventory, recipe) { canCookChecker(recipe) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = recipe.name) },
        text = {
            Column {
                Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ingrediente Necesare:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                recipe.ingredientsNeeded.forEach { (ingredientId, quantityNeeded) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        val drawableResId = tileDrawables[ingredientId]
                        if (drawableResId != null) {
                            Image(painterResource(id = drawableResId), contentDescription = null, modifier = Modifier.size(20.dp))
                        } else { Box(Modifier.size(20.dp).background(tileColors[ingredientId] ?: Color.Gray, CircleShape)) }
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
                TextButton(onClick = { onCook(recipe) }, enabled = canCookCurrentRecipe) { Text("Gătește") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Înapoi") }
            }
        }
    )
}

@Composable
fun GameTile(
    modifier: Modifier = Modifier,
    type: Int,
    size: Dp,
    isSelected: Boolean,
    isDisappearing: Boolean,
    animatedOffset: IntOffset,
    onClick: () -> Unit
) {
    val disappearingScale = remember { Animatable(1f) }
    val disappearingAlpha = remember { Animatable(1f) }
    LaunchedEffect(isDisappearing) {
        if (isDisappearing) {
            launch { disappearingScale.animateTo(0.3f, tween(300)) }
            launch { disappearingAlpha.animateTo(0f, tween(300)) }
        } else {
            if (disappearingScale.value != 1f) disappearingScale.snapTo(1f)
            if (disappearingAlpha.value != 1f) disappearingAlpha.snapTo(1f)
        }
    }
    val selectionModifier = if (isSelected) Modifier.border(2.dp, Color.Yellow, MaterialTheme.shapes.small).scale(1.05f) else Modifier
    val drawableResId = tileDrawables[type]
    Box(
        modifier = modifier
            .offset { animatedOffset }
            .graphicsLayer {
                scaleX = disappearingScale.value
                scaleY = disappearingScale.value
                this.alpha = disappearingAlpha.value
            }
            .size(size)
            .padding(1.dp)
            .then(selectionModifier)
            .background(tileColors[type]?.copy(alpha = 0.4f) ?: Color.Gray.copy(alpha = 0.4f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (drawableResId != null) {
            Image(painter = painterResource(id = drawableResId), contentDescription = getIngredientName(type), modifier = Modifier.fillMaxSize(0.8f))
        }
    }
}


@Composable
fun GameBoard(
    board: List<List<Int>>,
    selectedTilePosition: TilePosition?,
    tilesBeingMatched: Set<TilePosition>,
    swappingTilesInfo: Pair<TilePosition, TilePosition>?,
    tile1AnimatedOffset: IntOffset,
    tile2AnimatedOffset: IntOffset,
    tileMovements: List<TileMovementInfo>,
    onTileClick: (row: Int, col: Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFA0A0A0)).padding(4.dp)
    ) {
        val tileSize = maxWidth / COLS
        val tileSizePx = with(LocalDensity.current) { tileSize.toPx() }
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                board.forEachIndexed { rowIndex, rowData ->
                    Row {
                        rowData.forEachIndexed { colIndex, logicalTileType ->
                            val currentPos = TilePosition(rowIndex, colIndex)
                            val isTileMovingToThisSpot = tileMovements.any { it.finalRow == rowIndex && it.col == colIndex }
                            val isDisappearing = tilesBeingMatched.contains(currentPos)
                            if ((logicalTileType != EMPTY_TILE && !isTileMovingToThisSpot) || isDisappearing) {
                                val isSelected = currentPos == selectedTilePosition
                                val swapOffset = when (currentPos) {
                                    swappingTilesInfo?.first -> tile1AnimatedOffset
                                    swappingTilesInfo?.second -> tile2AnimatedOffset
                                    else -> IntOffset.Zero
                                }
                                GameTile(
                                    type = logicalTileType, size = tileSize, isSelected = isSelected,
                                    isDisappearing = isDisappearing, animatedOffset = swapOffset,
                                    onClick = { onTileClick(rowIndex, colIndex) }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(tileSize))
                            }
                        }
                    }
                }
            }
            tileMovements.forEach { movement ->
                val initialOffsetY = if (movement.isNew) -tileSizePx * (movement.finalRow.toFloat() + 3f) else -(movement.fallDistance * tileSizePx)
                val initialAlpha = if (movement.isNew) 0f else 1f
                val translationY = remember(movement) { Animatable(initialOffsetY) }
                val alpha = remember(movement) { Animatable(initialAlpha) }
                LaunchedEffect(movement) {
                    val delayMillis = (movement.col * 30L + movement.fallDistance * 10L).toInt()
                    launch { translationY.animateTo(0f, tween(350, delayMillis)) }
                    if (movement.isNew) { launch { alpha.animateTo(1f, tween(350, delayMillis)) } }
                    else { alpha.snapTo(1f) }
                }
                val finalPosX = movement.col * tileSizePx
                val finalPosY = movement.finalRow * tileSizePx
                val finalPos = TilePosition(movement.finalRow, movement.col)
                val swapOffset = when (finalPos) {
                    swappingTilesInfo?.first -> tile1AnimatedOffset
                    swappingTilesInfo?.second -> tile2AnimatedOffset
                    else -> IntOffset.Zero
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer { this.alpha = alpha.value }
                        .offset { IntOffset(x = finalPosX.toInt() + swapOffset.x, y = finalPosY.toInt() + translationY.value.toInt() + swapOffset.y) }
                ) {
                    GameTile(
                        type = movement.tileType, size = tileSize,
                        isSelected = (finalPos == selectedTilePosition), isDisappearing = false,
                        animatedOffset = IntOffset.Zero,
                        onClick = { onTileClick(movement.finalRow, movement.col) }
                    )
                }
            }
        }
    }
}


@Composable
fun ShopDialog(
    cookedMeals: Map<Int, Int>,
    recipesData: List<Recipe>,
    onSellAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var totalValue = 0
    cookedMeals.forEach { (recipeId, quantity) ->
        val recipe = recipesData.find { it.id == recipeId }
        if (recipe != null) { totalValue += recipe.sellingPrice * quantity }
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
                    LazyColumn {
                        items(cookedMeals.entries.toList(), key = { it.key }) { (recipeId, quantity) ->
                            val recipe = recipesData.find { it.id == recipeId }
                            if (recipe != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val firstIngredientId = recipe.ingredientsNeeded.keys.firstOrNull()
                                        val drawableResId = if(firstIngredientId != null) tileDrawables[firstIngredientId] else null
                                        if (drawableResId != null) {
                                            Image(painterResource(id = drawableResId), contentDescription = null, modifier = Modifier.size(24.dp))
                                        } else { Box(Modifier.size(24.dp)) }
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
                Button(onClick = onSellAll, enabled = cookedMeals.isNotEmpty()) { Text("Vinde Tot") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Închide") }
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Match3PuzzleGameTheme {
        // MODIFICAT: Preview-ul acum arată direct Meniul sau un ecran specific dacă dorești.
        MainMenuScreen(onNavigateToGame = {}, onNavigateToRecipeBook = {}, onNavigateToUpgrades = {}, onNavigateToShop = {})
        // Sau pentru a previzualiza harta (cu date mock):
        // WorldMapScreenPreview()
    }
}

// MODIFICAT: `onNavigateToGame` acum duce la WORLD_MAP (s-a specificat în Match3GameApp)
@Composable
fun MainMenuScreen(
    onNavigateToGame: () -> Unit, // Acesta va fi pentru WORLD_MAP
    onNavigateToRecipeBook: () -> Unit,
    onNavigateToUpgrades: () -> Unit,
    onNavigateToShop: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cronicile Culinare", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Bucătăria Călătoare", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 32.dp))

        Button(
            onClick = { playSound(context, R.raw.click); onNavigateToGame() }, // Navighează la WORLD_MAP
            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp)
        ) { Text("Pornește Aventura") } // MODIFICAT Text Buton

        Button(
            onClick = { playSound(context, R.raw.click); onNavigateToRecipeBook() },
            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp)
        ) { Text("Carte de Bucate") }

        Button(
            onClick = { playSound(context, R.raw.click); onNavigateToUpgrades() },
            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp)
        ) { Text("Atelier Îmbunătățiri") }

        Button(
            onClick = { playSound(context, R.raw.click); onNavigateToShop() },
            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp)
        ) { Text("Piață (Vinde)") }
    }
}


// NOU: WorldMapScreen Composable
@OptIn(ExperimentalMaterial3Api::class) // Pentru TopAppBar
@Composable
fun WorldMapScreen(
    allLevels: List<LevelData>,
    maxUnlockedLevelIndex: Int, // Indexul maxim deblocat în lista allLevels
    onSelectLevel: (levelIndex: Int) -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToRecipeBook: () -> Unit,
    onNavigateToUpgrades: () -> Unit,
    playerMoney: Int // Pentru afișare (opțional)
) {
    val context = LocalContext.current
    Log.d(TAG, "WorldMapScreen Composing. Max unlocked index: $maxUnlockedLevelIndex")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harta Aventurii Culinare") },
                navigationIcon = {
                    IconButton(onClick = {
                        playSound(context, R.raw.click)
                        onNavigateToMenu() // Buton pentru a reveni la meniul principal
                    }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Meniu Principal")
                    }
                },
                actions = {
                    // Afișare Bani (similar cu UpgradesScreen)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp) // Redus padding
                    ) {
                        Image(painterResource(id = R.drawable.coin), contentDescription = "Bani", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(playerMoney.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) // Font mai mic
                    }
                    // Buton Rețete
                    IconButton(onClick = { playSound(context, R.raw.click); onNavigateToRecipeBook() }) {
                        Image(painter = painterResource(id = R.drawable.carte), contentDescription = "Carte de Bucate", modifier = Modifier.size(30.dp))
                    }
                    // Buton Upgrade-uri
                    IconButton(onClick = { playSound(context, R.raw.click); onNavigateToUpgrades() }) {
                        Image(painter = painterResource(id = R.drawable.upgrade), contentDescription = "Atelier", modifier = Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Aplică padding-ul de la Scaffold
                .verticalScroll(rememberScrollState()), // Adăugăm scroll dacă nivelele depășesc ecranul
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TODO: Adaugă o imagine de fundal pentru hartă sau o imagine a autorulotei
            Image(
                painter = painterResource(id = R.drawable.map_background_placeholder), // ADAUGĂ o imagine placeholder în drawable
                contentDescription = "Fundal Hartă",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop // Sau .Fit
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Alege următoarea destinație pentru 'Le Zeste Mobile'!",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (allLevels.isEmpty()) {
                    Text("Niciun nivel definit încă.")
                } else {
                    allLevels.forEachIndexed { index, levelData ->
                        val isUnlocked = index <= maxUnlockedLevelIndex
                        Button(
                            onClick = {
                                if (isUnlocked) {
                                    playSound(context, R.raw.click)
                                    onSelectLevel(index) // Trimite INDEXUL nivelului
                                } else {
                                    playSound(context, R.raw.lost) // Sunet de acțiune invalidă
                                }
                            },
                            enabled = isUnlocked,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isUnlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Nivel ${levelData.levelId}: ${levelData.name}")
                                if (!isUnlocked) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_lock), // ADAUGĂ o iconiță de lacăt în drawable
                                        contentDescription = "Blocat",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // Afișează mesaj dacă toate nivelele sunt completate
                if (maxUnlockedLevelIndex >= allLevels.size -1 && allLevels.isNotEmpty()){
                    Spacer(modifier = Modifier.height(20.dp)) // Acest Spacer și Text-ul de mai jos
                    Text(                                      // sunt acum în Column-ul cu padding orizontal
                        "Felicitări, Chef! Ai explorat toate destinațiile cunoscute!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

        }
    }
}

// NOU: Preview pentru WorldMapScreen (opțional, pentru dezvoltare)
@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun WorldMapScreenPreview() {
    Match3PuzzleGameTheme {
        WorldMapScreen(
            allLevels = gameLevels.take(4), // Luăm doar primele 4 pentru preview
            maxUnlockedLevelIndex = 1, // Simulăm că primele 2 sunt deblocate
            onSelectLevel = {},
            onNavigateToMenu = {},
            onNavigateToRecipeBook = {},
            onNavigateToUpgrades = {},
            playerMoney = 1230
        )
    }
}