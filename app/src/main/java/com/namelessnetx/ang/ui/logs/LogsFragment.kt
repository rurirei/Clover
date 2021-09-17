package com.namelessnetx.ang.ui.logs

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.namelessnetx.ang.R

class LogsFragment : Fragment() {

    companion object {
        fun newInstance() = LogsFragment()
    }

    private lateinit var viewModel: LogsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.logs_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(LogsViewModel::class.java)
        // TODO: Use the ViewModel
    }

}