package com.namelessnetx.ang.ui.home

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.namelessnetx.ang.R

class HomeFragment : Fragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root =inflater.inflate(R.layout.home_fragment, container, false)

        //to inform our activity toolbar that we have a menu we shall
        //inform it below here by telling the fragment class to inform
        //its activity class that it has option menus
        setHasOptionsMenu(true)

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        // TODO: Use the ViewModel
    }

    //option menu only to how on the home activity fragment call
    // and closed when the activity fragment is closed
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.configurations, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        /**the format code structure of fragment is similar to the activity so it
         *  is very simple and easy to implement as follows
         * R.id.identification ->{
         * true
         * } **/
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO bindingView
    }

}