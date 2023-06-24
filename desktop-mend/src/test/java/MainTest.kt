package co.samco.mend4.desktop

import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.commands.Help
import commands.TestBase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainTest : TestBase() {
    private lateinit var main: Main
    private lateinit var commandRunnerComponent: CommandRunnerComponent
    private lateinit var argsCaptor: KArgumentCaptor<List<String>>

    @Before
    override fun setup() {
        super.setup()
        commandRunnerComponent = mock()
        argsCaptor = argumentCaptor()
        main = Main(strings, log)
    }

    @Test
    fun helpTest() {
        val help: Help = mock()
        whenever(commandRunnerComponent.helpCommand()).thenReturn(help)
        for (s in Command.HELP_ALIASES) {
            main.run(commandRunnerComponent, listOf(s))
        }
        verify(commandRunnerComponent, times(Command.HELP_ALIASES.size)).helpCommand()
        verify(help, times(Command.HELP_ALIASES.size))
            .executeCommand(argsCaptor.capture())
        Assert.assertTrue(argsCaptor.allValues.isEmpty())
    }

    @Test
    fun noArgs() {
        val defaultCommand: Command = mock()
        whenever(commandRunnerComponent.defaultCommand()).thenReturn(defaultCommand)
        main.run(commandRunnerComponent, emptyList())
        verify(commandRunnerComponent).defaultCommand()
        verify(defaultCommand).executeCommand(any())
    }

    @Test
    fun runCommandWithArgs() {
        val command: Command = mock()
        runCommand(listOf("hi"), command)
    }

    @Test
    fun runCommand() {
        val command: Command = mock()
        runCommand(emptyList(), command)
    }

    @Test
    fun commandFailed() {
        val command: Command = mock()
        whenever(command.executionResult).thenReturn(-1)
        runCommand(emptyList(), command)
    }

    private fun runCommand(subCommandArgs: List<String>, command: Command) {
        val commandName = "command"
        whenever(command.isCommandForString(commandName)).thenReturn(true)
        val commands: MutableSet<Command> = HashSet()
        commands.add(command)
        whenever(commandRunnerComponent.commands()).thenReturn(commands)
        val args: MutableList<String> = ArrayList()
        args.add(commandName)
        args.addAll(subCommandArgs)
        main.run(commandRunnerComponent, args)
        verify(commandRunnerComponent).commands()
        verify(command).isCommandForString(commandName)
        verify(command).executeCommand(argsCaptor.capture())
        assertThat(argsCaptor.allValues, equalTo(subCommandArgs))
    }
}