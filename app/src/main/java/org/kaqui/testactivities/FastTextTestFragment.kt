package org.kaqui.testactivities

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.method.KeyListener
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.UI
import org.jetbrains.anko.support.v4.runOnUiThread
import org.kaqui.R
import org.kaqui.model.Certainty
import org.kaqui.model.Kana
import org.kaqui.model.getQuestionText
import org.kaqui.model.text
import org.kaqui.setExtTint
import org.kaqui.showItemProbabilityData
import org.kaqui.wrapInScrollView
import kotlin.concurrent.schedule
import java.util.*

const val INPUT_DELAY_ON_RIGHT_ANSWER: Long = 100
const val INPUT_DELAY_ON_WRONG_ANSWER: Long = 500

class FastTextTestFragment : Fragment(), TestFragment {
    companion object {
        private const val TAG = "FastTextTestFragment"

        const val defaultInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        @JvmStatic
        fun newInstance() = FastTextTestFragment()
    }

    private lateinit var answerField: EditText
    private lateinit var testQuestionLayout: TestQuestionLayout

    private val testFragmentHolder
        get() = (activity!! as TestFragmentHolder)
    private val testEngine
        get() = testFragmentHolder.testEngine
    private val testType
        get() = testFragmentHolder.testType

    private val currentKana get() = testEngine.currentQuestion.contents as Kana
    private var shouldIgnoreTextInput = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)

        val questionMinSize = 30

        testQuestionLayout = TestQuestionLayout()
        val mainBlock = UI {
            testQuestionLayout.makeMainBlock(activity!!, this, questionMinSize, forceLandscape = true) {
                wrapInScrollView(this) {
                    verticalLayout {
                        answerField = editText {
                            gravity = Gravity.CENTER
                            inputType = TextTestFragment.defaultInputType
                            /*
                            setOnEditorActionListener { v, actionId, event ->
                                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL)
                                    if (event == null || event.action == KeyEvent.ACTION_DOWN)
                                        this@FastTextTestFragment.onTextAnswerClicked(v, Certainty.SURE)
                                    else if (event.action == KeyEvent.ACTION_UP)
                                        true
                                    else
                                        false
                                else
                                    false
                            }
                            answerKeyListener = keyListener
                            */

                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: Editable?) {
                                    answerTextDidChange(s)
                                }
                            })

                            filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                                if (shouldIgnoreTextInput) {
                                    ""
                                } else {
                                    source.filter { c: Char ->
                                        c.isLetter()
                                    }
                                }
                            })
                        }.lparams(width = matchParent)

                        linearLayout {
                            button(R.string.dont_know) {
                                setExtTint(R.attr.backgroundDontKnow)
                                setOnClickListener { this@FastTextTestFragment.onTextAnswerClicked(this, Certainty.DONTKNOW) }
                            }.lparams(width = matchParent)
                        }.lparams(width = matchParent, height = wrapContent)
                    }
                }
            }
        }.view

        testQuestionLayout.questionText.setOnLongClickListener {
            if (testEngine.currentDebugData != null)
                showItemProbabilityData(context!!, testEngine.currentQuestion.text, testEngine.currentDebugData!!)
            true
        }

        refreshQuestion()

        return mainBlock
    }

    override fun refreshQuestion() {
        testQuestionLayout.questionText.text = testEngine.currentQuestion.getQuestionText(testType)

        answerField.text.clear()
        answerField.requestFocus()
    }

    private fun onTextAnswerClicked(view: View, certainty: Certainty): Boolean {
        if (certainty == Certainty.DONTKNOW)
            answerField.text.clear()

        val result =
                if (certainty == Certainty.DONTKNOW) {
                    Certainty.DONTKNOW
                } else {
                    val answer = answerField.text.trim().toString().toLowerCase()

                    if (answer.isBlank())
                        return true

                    if (answer == currentKana.romaji) {
                        certainty
                    } else {
                        Certainty.DONTKNOW
                    }
                }

        testFragmentHolder.onAnswer(view, result, null)
        testFragmentHolder.nextQuestion()

        return true
    }

    private fun answerTextDidChange(s: Editable?) {
        val input = s.toString().toLowerCase()
        if (input.isEmpty()) {
            return
        }

        val romaji = currentKana.romaji.toLowerCase()
        val result =
                if (input == romaji) {
                    Certainty.SURE
                } else if (romaji.startsWith(input)) {
                    return
                } else {
                    Certainty.DONTKNOW
                }

        testFragmentHolder.onAnswer(view, result, null)
        testFragmentHolder.nextQuestion()

        shouldIgnoreTextInput = true

        val delay = if (result == Certainty.DONTKNOW) INPUT_DELAY_ON_WRONG_ANSWER else INPUT_DELAY_ON_RIGHT_ANSWER
        Timer("NextQuestionPause",false).schedule(delay) {
            runOnUiThread {
                shouldIgnoreTextInput = false
                refreshQuestion()
            }
        }
    }

    override fun setSensible(e: Boolean) {
        // do nothing
    }
}