package co.samco.mend4.desktop

import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.output.ExitManager
import commands.TestBase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever

class MainTest : TestBase() {
    private lateinit var main: Main
    private lateinit var commandRunnerComponent: CommandRunnerComponent
    private lateinit var argsCaptor: KArgumentCaptor<List<String>>

    private val exitManager: ExitManager = mock()

    @Before
    override fun setup() {
        super.setup()
        commandRunnerComponent = mock()
        argsCaptor = argumentCaptor()
        main = Main(
            strings = strings,
            log = log,
            exitManager = exitManager
        )
    }

    private fun commandMock(): Command = mock<Command>().apply {
        whenever(this.commandAliases).thenReturn(listOf("test"))
        whenever(this.usageText).thenReturn("usage")
        whenever(this.descriptionText).thenReturn("description")
    }

    @Test
    fun noArgs() {
        val defaultCommand: Command = commandMock()
        whenever(commandRunnerComponent.defaultCommand()).thenReturn(defaultCommand)
        main.run(commandRunnerComponent, emptyList())
        verify(commandRunnerComponent).defaultCommand()
        verify(defaultCommand).executeCommand(any())
    }

    @Test
    fun runCommandWithArgs() {
        val command: Command = commandMock()
        runCommand(listOf("hi"), command)
        verify(exitManager).exit(eq(0))
    }

    @Test
    fun runCommand() {
        val command: Command = commandMock()
        runCommand(emptyList(), command)
        verify(exitManager).exit(eq(0))
    }

    @Test
    fun commandFailed() {
        val command: Command = commandMock()
        whenever(command.executionResult).thenReturn(-1)
        runCommand(emptyList(), command)
        verify(exitManager).exit(eq(-1))
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
        assertThat(argsCaptor.firstValue, equalTo(subCommandArgs))
    }
}