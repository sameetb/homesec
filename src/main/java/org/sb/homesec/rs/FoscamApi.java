package org.sb.homesec.rs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.eclipse.jetty.util.MultiPartOutputStream;
import org.sb.homesec.Foscam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/foscam")
public class FoscamApi 
{
	private static final Logger log = LoggerFactory.getLogger(FoscamApi.class);
	
	final Map<String, Foscam> cams;
	
	public FoscamApi(Set<Foscam> cams) 
	{
		this.cams = cams.stream().collect(Collectors.toMap(c -> c.getName(), c -> c));
	}

	@Path("/names")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response names()
	{
		return Response.ok(cams.keySet().stream().collect(Collectors.collectingAndThen(jsonStringArray(), JsonArray::toString))).build();
	}
	
	@Path("/{name}/snapPicture")
	@GET
	@Produces("image/jpeg")
	public Response snapPicture(@PathParam("name") String name)
	{
		return Optional.ofNullable(cams.get(name))
					.map(cam -> Response.ok(new StreamingOutput() 
											{
												@Override
												public void write(OutputStream os) throws IOException, WebApplicationException 
												{
													cam.snapPicture((ba, size) -> 
													{
														try 
														{
															os.write(ba, 0, size);
														} 
														catch (IOException io) 
														{
															throw new RuntimeException(io);
														}
													});
													os.flush();
												}
											})
							             .header("Content-Disposition", 
													"attachment;filename=\"" + name + "-" + System.currentTimeMillis()+  ".jpg\""))
					 .orElse(Response.status(Response.Status.NOT_FOUND))
					 .build();
	}
	
	@Path("/{name}/arm")
	@POST
	public Response arm(@PathParam("name")String name) throws Exception
	{
		Foscam cam = cams.get(name);
		if(cam != null)
		{
			cam.awayarm();
			return Response.ok().build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@Path("/{name}/disarm")
	@POST
	public Response disarm(@PathParam("name")String name) throws Exception
	{
		Foscam cam = cams.get(name);
		if(cam != null)
		{
			cam.disarm();
			return Response.ok().build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@Path("/arm")
	@POST
	public Response armall() throws Exception
	{
		ArrayList<Exception> exs = null; 
		for(Foscam cam : cams.values())
			try 
			{
				cam.awayarm();
			} 
			catch (Exception e) 
			{
				log.error("Failed to arm " + cam.getName(), e);
				if(exs == null) exs = new ArrayList<>();
				exs.add(e);
			}
		
		if(exs == null)
			return Response.ok().build();
		
		throw throwEx(exs);
	}

	private Exception throwEx(ArrayList<Exception> exs) 
	{
		if(exs.size() == 1) return exs.get(0);
		
		IOException ex = new IOException("Partial success");
		for(Exception e : exs) ex.addSuppressed(e);
		return ex;
	}

	@Path("/disarm")
	@POST
	public Response disarmall() throws Exception
	{
		ArrayList<Exception> exs = null; 
		for(Foscam cam : cams.values())
			try 
			{
				cam.disarm();
			} 
			catch (Exception e) 
			{
				log.error("Failed to disarm " + cam.getName(), e);
				if(exs == null) exs = new ArrayList<>();
				exs.add(e);
			}
		
		if(exs == null)
			return Response.ok().build();
		
		throw throwEx(exs);
	}

	@Path("/snapPicture2")
	@GET
	@Produces(MediaType.MULTIPART_FORM_DATA)
	public Response snapPictureAllMP()
	{
		return Response.ok(new StreamingOutput() 
		{
			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException 
			{
				MultiPartOutputStream mpos = new MultiPartOutputStream(os);
				for(Foscam cam : cams.values())
				{
					boolean[] header = new boolean[]{false};
					try 
					{
						cam.snapPicture((ba, size) -> 
						{
							try 
							{
								if(header[0] == false)
								{
									mpos.startPart("image/jpeg", new String[]
											{
											 "Content-Disposition: attachment;filename=\"" + cam.getName() + "-" + System.currentTimeMillis()+  ".jpg\""
											});
									header[0] = true;
								}
								mpos.write(ba, 0, size);
							} 
							catch (IOException io) 
							{
								throw new RuntimeException(io);
							}
						});
					} 
					catch (Exception e) 
					{
						log.error("Failed to snap picture from " + cam.getName(), e);
					}
				}
				mpos.close();
			}
		}).build();
	}
	
	@Path("/snapPicture")
	@GET
	@Produces("application/zip")
	public Response snapPictureAll()
	{
		return Response.ok(new StreamingOutput() 
		{
			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException 
			{
				ZipOutputStream zos = new ZipOutputStream(os);
				for(Foscam cam : cams.values())
				{
					boolean[] header = new boolean[]{false};
					try 
					{
						cam.snapPicture((ba, size) -> 
						{
							try 
							{
								if(header[0] == false)
								{
									zos.putNextEntry(new ZipEntry(cam.getName() + "-" + System.currentTimeMillis()+  ".jpg"));
									header[0] = true;
								}
								zos.write(ba, 0, size);
							} 
							catch (IOException io) 
							{
								throw new RuntimeException(io);
							}
						});
					} 
					catch (IOException e) 
					{
						log.error("Failed to snap picture from " + cam.getName(), e);
					}
					finally
					{
						zos.closeEntry();
					}
				}
				zos.finish();
				zos.close();
			}
		})
	    .header("Content-Disposition", "attachment;filename=\"foscam-" + System.currentTimeMillis()+  ".zip\"")
		.build();
	}

	@Path("/status")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response statusAll()
	{
		return Response.ok(cams.values().stream()
										.map(cam -> camStatus(cam))
										.collect(Collectors.collectingAndThen(jsonArray(), JsonArray::toString))).build();
	}

	private static JsonObject camStatus(Foscam cam) 
	{
		try
		{
			return cam.getStatus();
		}
		catch(IOException io)
		{
			log.error("Failed to retrieve status of " + cam.getName(), io);
			return Json.createObjectBuilder().add("name", cam.getName()).add("error", io.getMessage()).build();
		}
	}
	
	@Path("/{name}/status")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response status(@PathParam("name") String name)
	{
		return Optional.ofNullable(cams.get(name)).map(cam -> camStatus(cam)).map(jsn -> Response.ok(jsn.toString()))
						.orElse(Response.status(Response.Status.NOT_FOUND))
						.build();
	}
	
	private Collector<JsonValue, JsonArrayBuilder, JsonArray> jsonArray()
	{
		return Collector.of(Json::createArrayBuilder, JsonArrayBuilder::add, (l, r) -> l.add(r), JsonArrayBuilder::build);
	}

	private Collector<String, JsonArrayBuilder, JsonArray> jsonStringArray()
	{
		return Collector.of(Json::createArrayBuilder, JsonArrayBuilder::add, (l, r) -> l.add(r), JsonArrayBuilder::build);
	}
	
	@Path("/{name}/record{duration:(/[0-9]+)?}")
	@POST
	public Response record(@PathParam("name")String name, @PathParam("duration")String durationSecs) throws Exception
	{
		Foscam cam = cams.get(name);
		if(cam != null)
		{
			cam.record(Optional.ofNullable(durationSecs).map(Integer::parseInt).orElse(60));
			return Response.ok().build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}
	
	@Path("/record{duration:(/[0-9]+)?}")
	@POST
	public Response recordall(@PathParam("duration")String durationSecs) throws Exception
	{
		int duration = Optional.ofNullable(durationSecs).map(Integer::parseInt).orElse(60);
		ArrayList<Exception> exs = null; 
		for(Foscam cam : cams.values())
			try 
			{
				cam.record(duration);
			} 
			catch (Exception e) 
			{
				log.error("Failed to record " + cam.getName(), e);
				if(exs == null) exs = new ArrayList<>();
				exs.add(e);
			}
		
		if(exs == null)
			return Response.ok().build();
		
		throw throwEx(exs);
	}

	@Path("/{name}/armcfg")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response armcfg(@PathParam("name") String name) throws IOException
	{
		Foscam cam = cams.get(name);
		if(cam != null)
			return Response.ok(cam.getMotionDetectConfig().toString()).build();
		return Response.status(Response.Status.NOT_FOUND).build();
	}
	
	@Path("/{name}/stream{duration:(/[0-9]+)?}")
	@GET
	@Produces("video/mp4")
	public Response stream(@PathParam("name")String name, @PathParam("duration")String durationSecs) throws Exception
	{
		return Optional.ofNullable(cams.get(name))
				.map(cam -> Response.ok(new StreamingOutput() 
										{
											@Override
											public void write(OutputStream os) throws IOException, WebApplicationException 
											{
												cam.stream(Optional.ofNullable(durationSecs).map(Integer::parseInt).orElse(30), 
														(ba, size) -> 
														{
															try 
															{
																os.write(ba, 0, size);
															} 
															catch (IOException io) 
															{
																throw new RuntimeException(io);
															}
														});
												os.flush();
											}
										})
						             .header("Content-Disposition", 
												"attachment;filename=\"" + name + "-" + System.currentTimeMillis()+  ".mp4\""))

				 .orElse(Response.status(Response.Status.NOT_FOUND))
				 .build();
	}
}
