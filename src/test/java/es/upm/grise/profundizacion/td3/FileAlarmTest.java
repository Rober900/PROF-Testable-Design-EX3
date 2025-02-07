package es.upm.grise.profundizacion.td3;

import static org.junit.jupiter.api.Assertions.assertFalse;import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class FileAlarmTest {
	
	FireAlarm fireAlarm;

	@SystemStub
	private EnvironmentVariables environmentVariables = new EnvironmentVariables("firealarm.location", System.getProperty("user.dir"));
	
	@BeforeEach
	public void setUp() throws ConfigurationFileProblemException, DatabaseProblemException {
		fireAlarm = new FireAlarm();
	}
	
	@Test
	public void test() throws SensorConnectionProblemException, IncorrectDataException {
		assertFalse(fireAlarm.isTemperatureTooHigh());
	}

	// 1º Test: Cuando no se puede localizar el fichero config.properties, la clase FireAlarm lanza una ConfigurationFileProblemException.
	@Test
	public void testConfigurationFileProblemException() throws ConfigurationFileProblemException, DatabaseProblemException {
		// Se controla la variable de entorno para que devuelva null
		environmentVariables.set("firealarm.location", null);
		assertThrows(ConfigurationFileProblemException.class, () -> new FireAlarm());
	}

	// 2º Test: Cualquier error de la base de datos, ej: problema de conexión o error en consulta, implica el lanzamiento de una DatabaseProblemException.
	@Test
	public void testDatabaseProblemException() throws DatabaseProblemException, IOException {

		// Se crea un directorio temporal y se obtiene su ruta
		String tmpdir = Files.createTempDirectory("tmpDirPrefix").toFile().getAbsolutePath();

		// Se crea el fichero config.properties en el directorio resources 
		Path tmpDirPath = Paths.get(tmpdir);
		Path resourcesDir = Files.createDirectory(tmpDirPath.resolve("resources"));
		File configFile = new File(resourcesDir + "/config.properties");
		configFile.createNewFile();

		// Se escribe en el archivo config la localizacion de la db y se le asigna a la variable de entorno ese valor
		FileWriter writer = new FileWriter(configFile);
		writer.write("dblocation = auxiliar");
		writer.close();

		environmentVariables.set("firealarm.location", tmpdir.toString());
		
		assertThrows(DatabaseProblemException.class, () -> new FireAlarm());
	}

	// 3º Test: Cuando el endpoint REST no es utilizable, la aplicación lanza una SensorConnectionProblemException.
	// Comprobación que devuelve la excepción en caso de que el endpoint este vacio
	@Test
	public void testSensorConnectionProblemExceptionEmpty() throws SensorConnectionProblemException {
		assertThrows(SensorConnectionProblemException.class, () -> fireAlarm.getTemperature("empty"));
	}

	// Comprobación que devuelve la excepción en caso de que el endpoint sea incorrecto
	// Modificación: He cambiado la variable sensors a package para poder acceder a ella desde el test
	@Test
	public void testSensorConnectionProblemExceptionWrong() throws SensorConnectionProblemException {
		fireAlarm.sensors.put("room", "https://github.com/Rober900/PROF-Testable-Design-EX3");
		assertThrows(SensorConnectionProblemException.class, () -> fireAlarm.getTemperature("room"));
	}

	// 4º Test: Si el objeto JSON devuelto no contiene la clave “temperature”, o el valor devuelto no es entero, la aplicación lanza una IncorrectDataException.
	// Modificación: La el objeto ObjestMapper se ha convertido en pakcage para poder acceder a él desde el test

	// Comprobación que devuelve la excepción en caso de que no se devuelva ningun JSON
	@Test
	public void testIncorrectDataExceptionEmpty() throws IncorrectDataException, JsonProcessingException {
		ObjectMapper objectMapper = mock(ObjectMapper.class);

		when(objectMapper.readTree("")).thenReturn(null);

		fireAlarm.mapper = objectMapper;
		assertThrows(IncorrectDataException.class, () -> fireAlarm.getTemperature("kitchen"));
	}

	// Comprobación que devuelve la excepción en caso de que el JSON no contenga la clave "temperature"
	@Test
	public void testIncorrectDataExceptionNoTemperature() throws IncorrectDataException, JsonProcessingException {
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		JsonNode result = mock(JsonNode.class);
		
		when(objectMapper.readTree("")).thenReturn(result);
		when(result.get("temperature")).thenReturn(null);

		fireAlarm.mapper = objectMapper;
		assertThrows(IncorrectDataException.class, () -> fireAlarm.getTemperature("kitchen"));
	}

	// Comprobación que devuelve la excepción en caso de que el JSON no contenga un valor entero
	@Test
	public void testIncorrectDataExceptionNotInteger() throws IncorrectDataException, JsonProcessingException {
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		JsonNode nodo = objectMapper.readTree("temperatura");
		
		when(objectMapper.readTree("")).thenReturn(nodo);

		fireAlarm.mapper = objectMapper;
		assertThrows(IncorrectDataException.class, () -> fireAlarm.getTemperature("kitchen"));
	}

	// 5º Test: Cuando todos los sensores devuelven una temperatura <= MAX_TEMPERATURE, el método isTemperatureTooHigh() devuelve false.
	@Test
	public void testIsTemperatureTooHighFalse() throws SensorConnectionProblemException, IncorrectDataException {

		// Se crea un objeto espia de FireAlarm para poder manipular el resultado de las temperaturas que recoge
		FireAlarm spyFireAlarm = spy(fireAlarm);
		doReturn(10).when(spyFireAlarm).getTemperature(anyString());
		assertFalse(spyFireAlarm.isTemperatureTooHigh());

	}

	// 6º Test: Cuando algún sensor devuelve una temperatura > MAX_TEMPERATURE, el método isTemperatureTooHigh() devuelve true.
	@Test
	public void testIsTemperatureTooHighTrue() throws SensorConnectionProblemException, IncorrectDataException {

		// Se crea un objeto espia de FireAlarm para poder manipular el resultado de las temperaturas que recoge
		FireAlarm spyFireAlarm = spy(fireAlarm);
		doReturn(100).when(spyFireAlarm).getTemperature(anyString());
		assertTrue(spyFireAlarm.isTemperatureTooHigh());

	}

}
