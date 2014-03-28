package com.whitebearsolutions.xml;

import java.util.List;

public interface VCalendar
{
	public VTimeZone getTimeZone();

	public void removeVevent(String uid);

	public void removeVtodo(String uid);

	public List<VEvent> getVevents();

	public boolean hasVevent(String uid);

	public void addVevent(VEvent ve);

	public List<VTodo> getVtodos();

	public void addVtodo(VTodo vt);

	public boolean hasVtodo(String uid);

	public VEvent getVevent(String uid);

	public VTodo getVtodo(String uid);

	public Iterable<VTodo> getVtodos(Period p);

	public VFreeBusy getFreeBusy(Period p);

	public Iterable<VEvent> getRecurrenceVevents(Period p);

	public Iterable<VTodo> getRecurrenceVtodos(Period p);

	public Iterable<VEvent> getVevents(Period p);

	public VFreeBusy getFreeBusy();

}
