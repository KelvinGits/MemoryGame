package com.abletechlabs.memorygame

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abletechlabs.memorygame.models.BoardSize
import com.abletechlabs.memorygame.models.MemoryGame
import com.abletechlabs.memorygame.models.UserImageList
import com.abletechlabs.memorygame.utils.EXTRA_DIFFICULTY_LEVEL
import com.abletechlabs.memorygame.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 254
    }

    private lateinit var adapter: MemoryBoardAdapter
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var memoryGame: MemoryGame
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private var boardSize: BoardSize = BoardSize.MEDIUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setupBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.hasWonGame()) {
                    showAlertDialog(
                        "Quit your current game?",
                        null,
                        View.OnClickListener { setupBoard() })
                } else {
                    setupBoard()
                }
                return true
            }
            R.id.mi_difficulty -> {
                showDifficultyDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName: String? = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(
            R.layout.dialog_download_board,
            null
        )
        showAlertDialog("Download custom game", boardDownloadView, View.OnClickListener {
            //Grab the text of the game name the user wants to download
            val etDownloadGame: EditText = boardDownloadView.findViewById<EditText>(
                R.id
                    .etDownloadGame
            )
            val gameToDownload: String = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)

        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList: UserImageList? = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry, we couldn't find such a game.", Snackbar.LENGTH_LONG)
                    .show()
                return@addOnSuccessListener
            }
            //Compute no. of numcards from images
            val numCards: Int = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You are now playing $customGameName", Snackbar.LENGTH_LONG)
                .show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showCreationDialog() {
        val difficultyView = LayoutInflater.from(this).inflate(
            R.layout.dialog_difficulty_level,
            null
        )
        val difficultyLevel: RadioGroup = difficultyView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog("Create your own game", difficultyView, View.OnClickListener {
            //Choose difficulty level
            val desiredDifficultyLevel: BoardSize = when (difficultyLevel.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //Navigate to new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_DIFFICULTY_LEVEL, desiredDifficultyLevel)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showDifficultyDialog() {
        val difficultyView = LayoutInflater.from(this).inflate(
            R.layout.dialog_difficulty_level,
            null
        )
        val difficultyLevel: RadioGroup = difficultyView.findViewById<RadioGroup>(R.id.radioGroup)

        //Automatically check the current difficulty level
        when (boardSize) {
            BoardSize.EASY -> difficultyLevel.check(R.id.rbEasy)
            BoardSize.MEDIUM -> difficultyLevel.check(R.id.rbMedium)
            BoardSize.HARD -> difficultyLevel.check(R.id.rbHard)
        }
        showAlertDialog("Choose difficulty level", difficultyView, View.OnClickListener {
            //Choose difficulty level
            boardSize = when (difficultyLevel.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveClickListener: View.OnClickListener
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object :
            MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        //Error-checking
        if (memoryGame.hasWonGame()) {
            Snackbar.make(clRoot, "You already won", Snackbar.LENGTH_LONG).show()
            return
        }

        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(clRoot, "Invalid move", Snackbar.LENGTH_SHORT).show()
            return
        }
        //Flip a card
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num of pairs found: ${memoryGame.numPairsFound}")
            var color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound}/${boardSize.getNumPairs()}"
            if (memoryGame.hasWonGame()) {
                Snackbar.make(clRoot, "Congratulations, you won!", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.CYAN,Color.RED,Color
                    .YELLOW)).oneShot()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()

    }
}