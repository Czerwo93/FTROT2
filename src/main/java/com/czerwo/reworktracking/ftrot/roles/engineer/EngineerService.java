package com.czerwo.reworktracking.ftrot.roles.engineer;

import com.czerwo.reworktracking.ftrot.auth.ApplicationUser;
import com.czerwo.reworktracking.ftrot.auth.ApplicationUserRepository;
import com.czerwo.reworktracking.ftrot.models.data.Day.Day;
import com.czerwo.reworktracking.ftrot.models.data.Day.DayName;
import com.czerwo.reworktracking.ftrot.models.data.Task;
import com.czerwo.reworktracking.ftrot.models.data.Week;
import com.czerwo.reworktracking.ftrot.models.dtos.DayDto;
import com.czerwo.reworktracking.ftrot.models.dtos.TaskDto;
import com.czerwo.reworktracking.ftrot.models.dtos.WeekDto;
import com.czerwo.reworktracking.ftrot.models.mappers.DayTasksMapper;
import com.czerwo.reworktracking.ftrot.models.mappers.TaskMapper;
import com.czerwo.reworktracking.ftrot.models.mappers.WeekDayMapper;
import com.czerwo.reworktracking.ftrot.models.repositories.DayRepository;
import com.czerwo.reworktracking.ftrot.models.repositories.TaskRepository;
import com.czerwo.reworktracking.ftrot.models.repositories.WeekRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EngineerService {

    private final ApplicationUserRepository applicationUserRepository;
    private final TaskRepository taskRepository;
    private final WeekRepository weekRepository;
    private final DayRepository dayRepository;
    private final TaskMapper taskMapper;
    private final DayTasksMapper dayTasksMapper;
    private final WeekDayMapper weekDayMapper;


    public EngineerService(ApplicationUserRepository applicationUserRepository,
                           TaskRepository taskRepository,
                           WeekRepository weekRepository,
                           DayRepository dayRepository,
                           TaskMapper taskMapper, DayTasksMapper dayTasksMapper, WeekDayMapper weekDayMapper) {
        this.applicationUserRepository = applicationUserRepository;
        this.taskRepository = taskRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.taskMapper = taskMapper;
        this.dayTasksMapper = dayTasksMapper;
        this.weekDayMapper = weekDayMapper;
    }

    public UserInfoDto getUserInfoByUsername(String username) {

        Optional<ApplicationUser> userByUsername = applicationUserRepository.findByUsernameWithTeamAndUserInfo(username);

        int unFinishedTasks = taskRepository
                .countTaskByAssignedEngineerIdAndStatusIsNotStatusFinished(
                        userByUsername.map(ApplicationUser::getId)
                                .orElseGet(() -> 0L));



        return UserInfoMapper.toDto(userByUsername, 0.15, unFinishedTasks);

    }

    public List<TaskSimplifyDto> getTasksForDay(String username) {

        Optional<ApplicationUser> userByUsername = applicationUserRepository
                .findByUsername(username);

        List<Task> AssignedTasksForDay = taskRepository
                .findAllByAssignedEngineerId(userByUsername
                .map(ApplicationUser::getId)
                .orElseThrow(() -> new RuntimeException()));

        return AssignedTasksForDay
                .stream()
                .map(TaskSimplifyMapper::toDto)
                .collect(Collectors.toList());

    }


    public int getTotalDurationOfAssignedTasksInCurrentWeek(String username, int weekNumber, int yearNumber){

       ApplicationUser userByUsername = applicationUserRepository
                .findByUsername(username).orElseThrow(() -> new RuntimeException());

        weekRepository
                .findByWeekNumberAndYearNumberAndUser(weekNumber, yearNumber, userByUsername)
                .orElseGet(() -> createWeek(userByUsername, weekNumber, yearNumber));

        return taskRepository.sumTasksByAssignerEngineerIdAndWeekAndYear(userByUsername.getId(), weekNumber, yearNumber);
    }




    public WeekDto getUserWeekWithTasks(String username, int weekNumber, int yearNumber){

        //todo check if week weekNumber and yearNumber can exist

        ApplicationUser userByUsername = applicationUserRepository.findByUsername(username).orElseThrow(() -> new RuntimeException());
        Week week = weekRepository
                .findByWeekNumberAndYearNumberAndUser(weekNumber, yearNumber, userByUsername)
                .orElseGet(() -> createWeek(userByUsername, weekNumber, yearNumber));

        List<Day> daysByWeekId = dayRepository.findAllByWeekId(week.getId());

        List<DayDto> dayDtos = new LinkedList<>();

        for (Day day: daysByWeekId) {
            List<TaskDto> taskDtos = taskRepository.findAllByDayId(day.getId())
                    .stream()
                    .map(task -> {
                        //todo plannedat and assigned engineer
                        return taskMapper.toDto(task, LocalDate.now(), "Repela");
                    })
                    .collect(Collectors.toList());

            DayDto dayDto = dayTasksMapper.toDto(day, taskDtos);
            dayDtos.add(dayDto);
        }

        return weekDayMapper.toDto(week,dayDtos);
    }


    @Transactional
    public Week createWeek(ApplicationUser user, int weekNumber, int yearNumber) {



        weekRepository
                .findByWeekNumberAndYearNumberAndUser(weekNumber,yearNumber,user)
                .ifPresent(item ->{
                    throw new RuntimeException();
                });


        Week week = new Week();
        week.setUser(user);
        week.setWeekNumber(weekNumber);
        week.setYearNumber(yearNumber);


        for (DayName dayName : DayName.values()) {
            Day day = new Day();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.WEEK_OF_YEAR, weekNumber);
            cal.set(Calendar.DAY_OF_WEEK, DayName.getCalendarDay(dayName));
            day.setDate(LocalDate.parse(sdf.format(cal.getTime())));
            day.setDayName(dayName);
            day.setWeek(week);

            week.addDayToWeek(day);

        }

        return weekRepository.save(week);

    }

}
